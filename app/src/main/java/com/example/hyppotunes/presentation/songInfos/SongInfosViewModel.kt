package com.example.hyppotunes.presentation.songInfos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hyppotunes.core.entity.Song
import com.example.hyppotunes.core.entity.SongInfo
import com.example.hyppotunes.core.repository.LocalSongsRepository
import com.example.hyppotunes.core.repository.RemoteSongsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class SongInfosViewModel(application: Application) : AndroidViewModel(application) {
    private val _filteredSongInfosStateFlow = MutableStateFlow<List<SongInfoModel>>(listOf())
    val filteredSongInfosStateFlow = _filteredSongInfosStateFlow.asStateFlow()

    private val _songInfosStateFlow = MutableStateFlow<List<SongInfoModel>>(listOf())
    private val songInfosStateFlow = _songInfosStateFlow.asStateFlow()

    private val _loadingStateFlow = MutableStateFlow(false)
    val loadingStateFlow = _loadingStateFlow.asStateFlow()

    private val _currentlyPlayingSongInfoStateFlow = MutableStateFlow<SongInfoModel?>(null)
    private val currentlyPlayingSongInfoStateFlow = _currentlyPlayingSongInfoStateFlow.asStateFlow()

    private val _keywordStateFlow = MutableStateFlow("")
    val keywordStateFlow = _keywordStateFlow.asStateFlow()

    private val _songsFilterStateFlow = MutableStateFlow(SongInfosFilter.Mixed)
    val songsFilterStateFlow = _songsFilterStateFlow.asStateFlow()

    private var localSongsRepository = LocalSongsRepository(application.applicationContext)

    private val songsDirectory = application.filesDir.path

    private var updatedKeywordWhileLocal = false

    fun updateKeyword(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _keywordStateFlow.emit(keyword)
            updatedKeywordWhileLocal = songsFilterStateFlow.value == SongInfosFilter.Local
            _filteredSongInfosStateFlow.emit(listOf())
            _loadingStateFlow.emit(true)
            val songInfos = when (songsFilterStateFlow.value) {
                SongInfosFilter.Mixed -> fetchSongInfosMixed()
                SongInfosFilter.Local -> fetchSongInfosLocal()
            }
            _songInfosStateFlow.emit(songInfos)
            val songInfosFiltered = getSongInfosWithFilters()
            _loadingStateFlow.emit(false)
            _filteredSongInfosStateFlow.emit(songInfosFiltered)
        }
    }

    fun updateSongsFilter(filter: SongInfosFilter) {
        viewModelScope.launch(Dispatchers.Default) {
            _songsFilterStateFlow.emit(filter)
            if (updatedKeywordWhileLocal && filter != SongInfosFilter.Local) {
                updateKeyword(keywordStateFlow.value)
            } else {
                val songInfosFiltered = getSongInfosWithFilters()
                _filteredSongInfosStateFlow.emit(songInfosFiltered)
            }
        }
    }

    fun updateCurrentlyPlayingSongInfo(songInfoModel: SongInfoModel?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (songInfoModel != currentlyPlayingSongInfoStateFlow.value) {
                _currentlyPlayingSongInfoStateFlow.emit(songInfoModel)
                val songInfosFiltered = getSongInfosWithFilters()
                _filteredSongInfosStateFlow.emit(songInfosFiltered)
            }
        }
    }


    fun fetchSong(songInfoModel: SongInfoModel): Song? {
        return runBlocking {
            withContext(Dispatchers.IO) {
                localSongsRepository.songFetch(songInfoModel.name, songInfoModel.artist)
            }
        }
    }

    fun downloadSong(songInfoModel: SongInfoModel) {
        viewModelScope.launch(Dispatchers.IO) {
            _filteredSongInfosStateFlow.emit(listOf())
            _loadingStateFlow.emit(true)
            val outputFile =
                File("$songsDirectory/${songInfoModel.artist} - ${songInfoModel.name}.mp3")
            val songInfo =
                SongInfo(songInfoModel.name, songInfoModel.artist, songInfoModel.imagePath)
            val downloadedSuccessfully = RemoteSongsRepository.fetchSong(songInfo, outputFile)
            val song = Song(
                songInfo.name,
                songInfo.artist,
                songInfo.imagePath,
                outputFile.name
            )
            if (downloadedSuccessfully) {
                localSongsRepository.songInsert(song)
            }
            val songInfos = songInfosStateFlow.value.map {
                if (song.name == it.name && song.artist == it.artist) {
                    SongInfoModel(
                        it.name,
                        it.artist,
                        it.imagePath,
                        downloadedSuccessfully,
                        it.isCurrentlyPlaying
                    )
                } else {
                    it
                }
            }.sortedBy {
                !it.isLocal
            }
            _songInfosStateFlow.emit(songInfos)
            val songInfosFiltered = getSongInfosWithFilters()
            _loadingStateFlow.emit(false)
            _filteredSongInfosStateFlow.emit(songInfosFiltered)
        }
    }

    fun validateSongInfos() {
        viewModelScope.launch(Dispatchers.IO) {
            val songInfos = songInfosStateFlow.value.filter {
                if (!it.isLocal) true
                else {
                    localSongsRepository.songFetch(it.name, it.artist) != null
                }
            }
            _songInfosStateFlow.emit(songInfos)
            val songInfosFiltered = getSongInfosWithFilters()
            _filteredSongInfosStateFlow.emit(songInfosFiltered)
        }
    }

    private suspend fun fetchSongInfosMixed(): List<SongInfoModel> {
        val localSongInfoModels = fetchSongInfosLocal()

        val remoteSongInfoModels = RemoteSongsRepository.fetchSongInfos(keywordStateFlow.value)
            .filter { original ->
                !localSongInfoModels.any { it.name == original.name && it.artist == original.artist }
            }.map {
                SongInfoModel(
                    it.name,
                    it.artist,
                    it.imagePath,
                    isLocal = false,
                    isCurrentlyPlaying = false
                )
            }

        return (localSongInfoModels + remoteSongInfoModels)
    }

    private suspend fun fetchSongInfosLocal(): List<SongInfoModel> {
        val localSongInfoModels =
            localSongsRepository.songInfosFetch(keywordStateFlow.value)?.map {
                SongInfoModel(
                    it.name,
                    it.artist,
                    it.imagePath,
                    isLocal = true,
                    isCurrentlyPlaying = false
                )
            } ?: listOf()
        val currentSong = currentlyPlayingSongInfoStateFlow.value
        return if (currentSong != null) {
            localSongInfoModels.map {
                if (it.name == currentSong.name && it.artist == currentSong.artist) {
                    currentSong
                } else {
                    it
                }
            }.sortedBy {
                !it.isCurrentlyPlaying
            }
        } else {
            localSongInfoModels
        }
    }

    private fun getSongInfosWithFilters(): List<SongInfoModel> {
        var songInfosFiltered = songInfosStateFlow.value
        if (songsFilterStateFlow.value == SongInfosFilter.Local) {
            songInfosFiltered = songInfosFiltered.filter {
                it.isLocal
            }
        }
        currentlyPlayingSongInfoStateFlow.value?.let { currentSong ->
            songInfosFiltered = songInfosFiltered.map {
                if (it.name == currentSong.name && currentSong.artist == it.artist) {
                    currentSong
                } else {
                    it
                }
            }.sortedBy {
                !it.isCurrentlyPlaying
            }
        }
        return songInfosFiltered
    }
}