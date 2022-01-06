package com.example.hyppotunes.presentation.player

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import com.example.hyppotunes.R
import com.example.hyppotunes.core.entity.Song
import com.example.hyppotunes.core.repository.LocalSongsRepository
import com.example.hyppotunes.databinding.ActivityPlayerBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import java.lang.StringBuilder

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var playerService: PlayerService
    private var isBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlayerService.PlayerServiceBinder
            playerService = binder.service
            isBound = true
            initObservers()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }
    private val localSongsRepository = LocalSongsRepository(this)
    private var playerObserversSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra("song")

        if (json == null) {
            finish()
            return
        }

        startService(json)

        binding.playerSongNameArtist.isSelected = true
        binding.backButton2.setOnClickListener {
            finish()
        }
        binding.deleteSongButton.setOnClickListener {
            val builder = AlertDialog.Builder(this).apply {
                setTitle("Confirm")
                setMessage("Delete song?")
                setCancelable(false)
                setPositiveButton("Ok") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                    if (isBound) {
                        playerService.songStateFlow.value?.let {
                            deleteSong(it)
                        }
                        playerService.stop()
                    }
                    finish()
                }
                setNegativeButton("Cancel") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
            }
            builder.show()
        }
        binding.playerStop.setOnClickListener {
            if (isBound) {
                playerService.stop()
            }
            finish()
        }
        binding.playerPlayPause.setOnClickListener {
            if (isBound) {
                playerService.playPausePlayer()
            }
        }
        binding.playerFastRewind.setOnClickListener {
            if (isBound) {
                playerService.fastRewindPlayer()
            }
        }
        binding.playerPlayFastForward.setOnClickListener {
            if (isBound) {
                playerService.fastForwardPlayer()
            }
        }
        binding.playerLoop.setOnClickListener {
            if (isBound) {
                playerService.loopPlayer()
            }
        }
        binding.playerTimeSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (isBound) {
                    playerService.startedSeeking = true
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    if (isBound) {
                        playerService.playerSeekTo(it.progress)
                        playerService.startedSeeking = false
                    }
                }
            }
        })
    }


    override fun onResume() {
        super.onResume()
        if (!isBound) {
            val intent = Intent(this, PlayerService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun startService(songJson: String) {
        val intent = Intent(this, PlayerService::class.java)
        intent.putExtra("song", songJson)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun initObservers() {
        if (!playerObserversSetup) {
            lifecycleScope.launchWhenResumed {
                playerService.songStateFlow.collectLatest {
                    it?.let { song ->
                        val text = "${song.artist} - ${song.name}"
                        binding.playerSongNameArtist.text = text
                        binding.playerSongName.text = song.name
                    }
                }
            }
            lifecycleScope.launchWhenResumed {
                playerService.songProgressStateFlow.collectLatest {
                    binding.playerTimeSeekBar.progress = it
                    binding.playerTimeStart.text = formatTime(it)
                }
            }
            lifecycleScope.launchWhenResumed {
                playerService.songDurationStateFlow.collectLatest {
                    binding.playerTimeSeekBar.max = it
                    binding.playerTimeEnd.text = formatTime(it)
                }
            }
            lifecycleScope.launchWhenResumed {
                playerService.songIsPlayingStateFlow.collectLatest {
                    if (it) {
                        binding.playerPlayPause.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24)
                    } else {
                        binding.playerPlayPause.setImageResource(R.drawable.ic_baseline_play_circle_outline_24)
                    }
                }
            }
            lifecycleScope.launchWhenResumed {
                playerService.songIsLoopingStateFlow.collectLatest {
                    if (it) {
                        binding.playerLoop.setImageResource(R.drawable.ic_baseline_loop_pink_24)
                    } else {
                        binding.playerLoop.setImageResource(R.drawable.ic_baseline_loop_24)
                    }
                }
            }
            playerObserversSetup = true
        }
    }

    private fun deleteSong(song: Song) {
        deleteFile(song.filePath)
        runBlocking {
            localSongsRepository.songDelete(song)
        }
    }

    private fun formatTime(duration: Int): String {
        val builder = StringBuilder()
        val min = duration / 1000 / 60
        val sec = (duration / 1000) % 60
        builder.append(min)
        builder.append(":")
        if (sec < 10) {
            builder.append("0")
        }
        builder.append(sec)
        return builder.toString()
    }
}