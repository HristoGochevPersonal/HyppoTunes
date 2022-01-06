package com.example.hyppotunes.presentation.songInfos

import com.example.hyppotunes.presentation.search.SearchActivity
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hyppotunes.databinding.ActivityMainBinding
import com.example.hyppotunes.presentation.player.PlayerActivity
import com.example.hyppotunes.presentation.player.PlayerService
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import com.example.hyppotunes.presentation.about.AboutActivity
import android.app.AlertDialog
import com.example.hyppotunes.R
import com.example.hyppotunes.presentation.utils.isServiceRunning

class SongInfosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: SongInfosViewModel
    private lateinit var songInfosAdapter: SongInfosAdapter

    private var lastNavAction = SongInfosNavAction.BrowseMixed
    private var newNavAction = SongInfosNavAction.BrowseMixed

    private lateinit var playerService: PlayerService
    private var isBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlayerService.PlayerServiceBinder
            playerService = binder.service
            isBound = true
            playerService.songStateFlow.value?.let { song ->
                val songInfoModel = SongInfoModel(
                    song.name,
                    song.artist,
                    null,
                    isLocal = true,
                    isCurrentlyPlaying = true
                )
                viewModel.updateCurrentlyPlayingSongInfo(songInfoModel)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private var backFromSearchActivity = false
    private val searchContract =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val intent = it.data ?: Intent().putExtra("keyword", "")
                val keyword = intent.getStringExtra("keyword") ?: ""
                viewModel.updateKeyword(keyword)
                backFromSearchActivity = true
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[SongInfosViewModel::class.java]

        initRecyclerView()
        initViewModelObservers()

        binding.navBarInstance.navButton.setOnClickListener {
            if (!viewModel.loadingStateFlow.value) {
                binding.root.openDrawer(GravityCompat.START)
            }
        }
        binding.navBarInstance.searchButton.setOnClickListener {
            if (!viewModel.loadingStateFlow.value) {
                openSearch()
            }
        }
        binding.navBarInstance.searchBarInput.setOnClickListener {
            if (!viewModel.loadingStateFlow.value) {
                openSearch()
            }
        }
        binding.drawer.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.tab0 -> {
                    newNavAction = SongInfosNavAction.BrowseMixed
                }
                R.id.tab1 -> {
                    newNavAction = SongInfosNavAction.BrowseLocal
                }
                R.id.tab2 -> {
                    newNavAction = SongInfosNavAction.About
                }
            }
            binding.root.closeDrawer(GravityCompat.START)
            true
        }
        binding.root.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerClosed(drawerView: View) {
                if (lastNavAction != newNavAction) {
                    if (newNavAction != SongInfosNavAction.About) {
                        lastNavAction = newNavAction
                    }
                    when (newNavAction) {
                        SongInfosNavAction.BrowseMixed -> viewModel.updateSongsFilter(
                            SongInfosFilter.Mixed
                        )
                        SongInfosNavAction.BrowseLocal -> viewModel.updateSongsFilter(
                            SongInfosFilter.Local
                        )
                        SongInfosNavAction.About -> openAbout()
                    }
                }
            }
        })

        viewModel.updateKeyword("")
    }

    override fun onPause() {
        super.onPause()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isBound) {
            if (isServiceRunning(PlayerService::class.java)) {
                val intent = Intent(this, PlayerService::class.java)
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            } else {
                if (backFromSearchActivity) {
                    backFromSearchActivity = false
                } else {
                    viewModel.validateSongInfos()
                    viewModel.updateCurrentlyPlayingSongInfo(null)
                }
            }
        }
    }


    private fun openSearch() {
        val intent = Intent(this, SearchActivity::class.java)
        searchContract.launch(intent)
    }

    private fun openAbout() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun openPlayer(songInfoModel: SongInfoModel) {
        val song = viewModel.fetchSong(songInfoModel) ?: return
        val intent = Intent(this, PlayerActivity::class.java)
        val gson = Gson()
        val json = gson.toJson(song)
        intent.putExtra("song", json)
        startActivity(intent)
    }

    private fun downloadSong(songInfoModel: SongInfoModel) {
        val builder = AlertDialog.Builder(this).apply {
            setTitle("Download")
            setMessage("${songInfoModel.artist} - ${songInfoModel.name}")
            setCancelable(false)
            setPositiveButton("Ok") { dialogInterface, _ ->
                dialogInterface.dismiss()
                viewModel.downloadSong(songInfoModel)
            }
            setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
        }
        builder.show()
    }

    private fun initRecyclerView() {
        val interaction = object : SongInfosAdapter.Interaction {
            override fun onItemSelected(position: Int, item: SongInfoModel) {
                if (item.isLocal) {
                    if (!viewModel.loadingStateFlow.value) {
                        openPlayer(item)
                    }
                } else {
                    if (!viewModel.loadingStateFlow.value) {
                        downloadSong(item)
                    }
                }
            }
        }

        songInfosAdapter = SongInfosAdapter(interaction)
        binding.songsRecyclerView.apply {
            this.layoutManager = LinearLayoutManager(this.context)
            this.adapter = songInfosAdapter
        }
    }

    private fun initViewModelObservers() {
        lifecycleScope.launchWhenStarted {
            viewModel.filteredSongInfosStateFlow.collectLatest {
                if (it.isEmpty()) {
                    songInfosAdapter.submitList(it)
                    binding.noSongsText.visibility = View.VISIBLE
                } else {
                    binding.noSongsText.visibility = View.GONE
                    songInfosAdapter.submitList(it)
                }
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.keywordStateFlow.collectLatest {
                binding.navBarInstance.searchBarInput.text = it
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.loadingStateFlow.collectLatest {
                if (it) {
                    if (binding.noSongsText.visibility == View.VISIBLE) {
                        binding.noSongsText.visibility = View.GONE
                    }
                    binding.songsLoadingDots.visibility = View.VISIBLE
                } else {
                    binding.songsLoadingDots.visibility = View.GONE
                    if (viewModel.filteredSongInfosStateFlow.value.isEmpty() &&
                        binding.noSongsText.visibility == View.GONE
                    ) {
                        binding.noSongsText.visibility = View.VISIBLE
                    }
                }
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.songsFilterStateFlow.collectLatest {
                val newText = when (it) {
                    SongInfosFilter.Mixed -> {
                        "Browse songs"
                    }
                    SongInfosFilter.Local -> {
                        "Browse local songs"
                    }
                }
                binding.songsHeader.text = newText
            }
        }
    }
}