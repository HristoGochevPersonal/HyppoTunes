package com.example.hyppotunes.presentation.songInfos

data class SongInfoModel(
    val name: String,
    val artist: String,
    val imagePath: String? = null,
    val isLocal: Boolean = false,
    val isCurrentlyPlaying: Boolean = false
)