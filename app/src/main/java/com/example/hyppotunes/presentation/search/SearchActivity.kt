package com.example.hyppotunes.presentation.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hyppotunes.R
import com.example.hyppotunes.databinding.ActivitySearchBinding
import kotlinx.coroutines.flow.collectLatest
import java.util.*


class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var viewModel: SearchViewModel
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter

    private val speechToTextContract =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val intent = it.data ?: return@registerForActivityResult
                val matches = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?: return@registerForActivityResult
                if (matches.isNotEmpty()) {
                    returnResult(matches[0])
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SearchHistoryDataSource.sharedPreferences =
            getSharedPreferences("search_history", Context.MODE_PRIVATE)

        viewModel = ViewModelProvider(this).get(SearchViewModel::class.java)
        initRecyclerView()
        initObservers()

        binding.searchBarInstance.backButton.setOnClickListener {
            finish()
        }
        binding.searchBarInstance.searchBarInput.addTextChangedListener {
            it?.let{ text->
                if (text.isNotEmpty()){
                    binding.searchBarInstance.speechButton.setImageResource(R.drawable.ic_baseline_clear_24)
                }
                else{
                    binding.searchBarInstance.speechButton.setImageResource(R.drawable.ic_baseline_mic_24)
                }
            }
        }
        binding.searchBarInstance.searchBarInput.setOnEditorActionListener { caller, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                returnResult(caller.text.toString())
            }
            true
        }
        binding.searchBarInstance.speechButton.setOnClickListener {
            if (binding.searchBarInstance.searchBarInput.text.isNotEmpty()){
                binding.searchBarInstance.searchBarInput.text.clear()
            }
            else{
                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                speechIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak")
                speechToTextContract.launch(speechIntent)
            }

        }
        binding.deleteSearchHistoryButton.setOnClickListener{
            viewModel.clear()
        }
        binding.searchBarInstance.searchBarInput.requestFocus()
    }

    private fun initRecyclerView() {
        searchHistoryAdapter = SearchHistoryAdapter(object : SearchHistoryAdapter.Interaction {
            override fun onItemSelected(position: Int, item: SearchHistoryItem) {
                returnResult(item.name)
            }
        })
        binding.searchHistory.apply {
            this.layoutManager = LinearLayoutManager(this.context)
            this.adapter = searchHistoryAdapter
        }
    }

    private fun initObservers() {
        lifecycleScope.launchWhenStarted {
            viewModel.stateFlow.collectLatest {
                searchHistoryAdapter.submitList(it)
            }
        }
    }

    private fun returnResult(keyword: String) {
        viewModel.update(keyword)
        val intent = Intent()
        intent.putExtra("keyword", keyword)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}