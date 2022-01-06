package com.example.hyppotunes.core.entity


data class Song(
    val name: String,
    val artist: String,
    val imagePath: String? = null,
    val filePath: String
)