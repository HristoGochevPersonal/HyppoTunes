package com.example.hyppotunes.core.repository

import com.example.hyppotunes.core.entity.SongInfo
import com.example.hyppotunes.core.utils.FlowFileWriter
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import song_infos.SongInfos
import song_infos.SongInfosServiceGrpcKt
import songs.Request
import songs.SongsServiceGrpcKt
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

object RemoteSongsRepository : Closeable {
    private const val serverIP = "10.0.2.2"
    private const val serverPort = 8980
    private var channel =
        ManagedChannelBuilder.forAddress(serverIP, serverPort).usePlaintext().build()
    private val songInfosStub = SongInfosServiceGrpcKt.SongInfosServiceCoroutineStub(channel)
    private val songsStub = SongsServiceGrpcKt.SongsServiceCoroutineStub(channel)

    suspend fun fetchSongInfos(keyword: String): List<SongInfo> {
        val request = SongInfos.Request.newBuilder().apply {
            this.name = keyword
        }.build()
        return songInfosStub.withDeadlineAfter(1200, TimeUnit.MILLISECONDS)
            .getByName(request)
            .map { response ->
                SongInfo(response.name, response.artist)
            }.catch {
                println("Unable to retrieve songs from server")
            }.toList()
    }

    suspend fun fetchSong(songInfo: SongInfo, outputFile: File): Boolean {

        val flowFileWriter = FlowFileWriter.Builder().build(outputFile) ?: return false

        val request = Request.newBuilder().apply {
            this.name = songInfo.name
            this.artist = songInfo.artist
        }.build()

        var ready = false

        songsStub.get(request)
            .onEach { chunk ->
                if (!chunk.ready && chunk.buffer.size() > 0) {
                    val writeStatus = flowFileWriter.write(chunk.buffer.toByteArray())
                    check(writeStatus)
                } else {
                    ready = chunk.ready
                }
            }
            .catch { e ->
                e.printStackTrace()
            }
            .onCompletion {
                if (ready) flowFileWriter.finalize() else flowFileWriter.abort()
            }
            .collect {

            }

        return ready
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
