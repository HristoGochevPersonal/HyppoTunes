package com.example.hyppotunes.presentation.splash

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.hyppotunes.core.repository.LocalSongsRepository
import com.example.hyppotunes.databinding.ActivityLoadingBinding
import com.example.hyppotunes.presentation.songInfos.SongInfosActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class LoadingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoadingBinding
    private val localSongsRepository = LocalSongsRepository(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CoroutineScope(Dispatchers.IO).launch {
            val completedIn = measureTimeMillis {
                localSongsRepository.autoUpdate()
                localSongsRepository.validate()
            }
            if (completedIn < 1000)
                delay(1000 - completedIn)
        }.invokeOnCompletion {
            val intent = Intent(this, SongInfosActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}