package com.example.hyppotunes.presentation.about

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.hyppotunes.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton3.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        println("About activity destroyed")
    }
}