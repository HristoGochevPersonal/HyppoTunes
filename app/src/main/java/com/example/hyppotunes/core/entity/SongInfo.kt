package com.example.hyppotunes.core.entity

data class SongInfo(
    val name: String,
    val artist: String,
    val imagePath: String? = null,
)