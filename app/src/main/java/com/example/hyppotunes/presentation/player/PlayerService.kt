package com.example.hyppotunes.presentation.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.hyppotunes.R
import com.example.hyppotunes.core.entity.Song
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class PlayerService : LifecycleService() {
    private val myBinder = PlayerServiceBinder()
    private lateinit var mediaPlayer: MediaPlayer

    private val _songStateFlow = MutableStateFlow<Song?>(null)
    val songStateFlow = _songStateFlow.asStateFlow()
    private val _songProgressStateFlow = MutableStateFlow(0)
    val songProgressStateFlow = _songProgressStateFlow.asStateFlow()
    private val _songDurationStateFlow = MutableStateFlow(0)
    val songDurationStateFlow = _songDurationStateFlow.asStateFlow()
    private val _songIsPlayingStateFlow = MutableStateFlow(false)
    val songIsPlayingStateFlow = _songIsPlayingStateFlow.asStateFlow()
    private val _songIsLoopingStateFlow = MutableStateFlow(false)
    val songIsLoopingStateFlow = _songIsLoopingStateFlow.asStateFlow()

    private lateinit var notificationFastRewindActionBuilder: Notification.Action.Builder
    private lateinit var notificationFastForwardActionBuilder: Notification.Action.Builder
    private lateinit var notificationPauseActionBuilder: Notification.Action.Builder
    private lateinit var notificationPlayActionBuilder: Notification.Action.Builder
    private lateinit var notificationBuilder: Notification.Builder
    private var startedInForeground = false
    private var playerHasBeenStarted = false
    var startedSeeking = false

    private lateinit var updaterJob: Job
    private val channelId = "Music service channel Id"
    private val channelName = "Music service channel"
    private val actionPlayPause = "PLAY_PAUSE"
    private val actionFastRewind = "FAST_REWIND"
    private val actionFastForward = "FAST_FORWARD"
    private val notificationId = 1001

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return myBinder
    }

    inner class PlayerServiceBinder : Binder() {
        val service: PlayerService
            get() = this@PlayerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createNotificationBuilder()
        mediaPlayer = MediaPlayer()
        mediaPlayer.setOnCompletionListener {
            if (!mediaPlayer.isLooping) {
                lifecycleScope.launchWhenStarted {
                    _songIsPlayingStateFlow.emit(false)
                }
            }
        }
        updaterJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (!startedSeeking && mediaPlayer.isPlaying) {
                    if (mediaPlayer.currentPosition <= mediaPlayer.duration) {
                        _songProgressStateFlow.emit(mediaPlayer.currentPosition)
                    } else {
                        _songProgressStateFlow.emit(mediaPlayer.duration)
                    }
                }
                delay(250)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                actionFastRewind -> {
                    fastRewindPlayer()
                    return@let
                }
                actionPlayPause -> {
                    playPausePlayer()
                    return@let
                }
                actionFastForward -> {
                    fastForwardPlayer()
                    return@let
                }
            }


            val json = it.getStringExtra("song") ?: return@let
            val gson = Gson()
            val song: Song = gson.fromJson(json, Song::class.java)

            startPlayer(song)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        updaterJob.cancel()
        mediaPlayer.stop()
        mediaPlayer.reset()
        mediaPlayer.release()
        hideNotification()
        deleteNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)

            val manager = getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun deleteNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.deleteNotificationChannel(channelId)
        }
    }

    private fun createNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fastRewindIntent = Intent(this, PlayerService::class.java)
            fastRewindIntent.action = actionFastRewind

            val playPauseIntent = Intent(this, PlayerService::class.java)
            playPauseIntent.action = actionPlayPause

            val fastForwardIntent = Intent(this, PlayerService::class.java)
            fastForwardIntent.action = actionFastForward

            val fastRewindPendingIntent = PendingIntent.getForegroundService(
                this,
                0,
                fastRewindIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPausePendingIntent = PendingIntent.getForegroundService(
                this,
                0,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val fastForwardPendingIntent = PendingIntent.getForegroundService(
                this,
                0,
                fastForwardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val fastRewindIcon =
                Icon.createWithResource(this, R.drawable.ic_baseline_fast_rewind_icon_24)
            val fastForwardIcon =
                Icon.createWithResource(this, R.drawable.ic_baseline_fast_forward_icon_24)
            val pauseIcon =
                Icon.createWithResource(this, R.drawable.ic_baseline_pause_circle_outline_icon_24)
            val playIcon =
                Icon.createWithResource(this, R.drawable.ic_baseline_play_circle_outline_icon_24)


            notificationFastRewindActionBuilder =
                Notification.Action.Builder(fastRewindIcon, "Rewind", fastRewindPendingIntent)
            notificationFastForwardActionBuilder =
                Notification.Action.Builder(fastForwardIcon, "Forward", fastForwardPendingIntent)
            notificationPlayActionBuilder =
                Notification.Action.Builder(playIcon, "Play", playPausePendingIntent)
            notificationPauseActionBuilder =
                Notification.Action.Builder(pauseIcon, "Pause", playPausePendingIntent)

            notificationBuilder = Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.hippoo4)
        }
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            songStateFlow.value?.let {
                notificationBuilder.setContentTitle(it.artist)
                notificationBuilder.setContentText(it.name)
                val playerActivityIntent = Intent(this, PlayerActivity::class.java)
                val gson = Gson()
                val json = gson.toJson(songStateFlow.value)
                playerActivityIntent.putExtra("song", json)


                val playerPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
                    addNextIntentWithParentStack(playerActivityIntent)
                    getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE )
                }

                notificationBuilder.setContentIntent(playerPendingIntent)
            }

            if (songIsPlayingStateFlow.value) {
                notificationBuilder.setActions(
                    notificationFastRewindActionBuilder.build(),
                    notificationPauseActionBuilder.build(),
                    notificationFastForwardActionBuilder.build()
                )
            } else {
                notificationBuilder.setActions(
                    notificationFastRewindActionBuilder.build(),
                    notificationPlayActionBuilder.build(),
                    notificationFastForwardActionBuilder.build()
                )
            }
            notificationBuilder.style =
                Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (!startedInForeground) {
                startForeground(notificationId, notificationBuilder.build())
                startedInForeground = true
            } else {
                notificationManager.notify(notificationId, notificationBuilder.build())
            }
        }
    }

    private fun hideNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }
    }

    private fun startPlayer(song: Song) {
        if (songStateFlow.value == song) {
            return
        }

        if (playerHasBeenStarted) {
            mediaPlayer.stop()
            mediaPlayer.reset()
        } else {
            playerHasBeenStarted = true
        }

        val songPath = getFileStreamPath(song.filePath).path

        mediaPlayer.setOnPreparedListener {
            lifecycleScope.launchWhenStarted {
                _songStateFlow.emit(song)
                _songIsPlayingStateFlow.emit(true)
                _songProgressStateFlow.emit(mediaPlayer.currentPosition)
                _songDurationStateFlow.emit(mediaPlayer.duration)
                updateNotification()
            }
            mediaPlayer.start()
        }

        mediaPlayer.setDataSource(songPath)
        mediaPlayer.prepareAsync()
    }

    fun stop() {
        stopSelf()
    }

    fun loopPlayer() {
        lifecycleScope.launchWhenStarted {
            mediaPlayer.isLooping = !mediaPlayer.isLooping
            _songIsLoopingStateFlow.emit(mediaPlayer.isLooping)
        }
    }

    fun playPausePlayer() {
        lifecycleScope.launchWhenStarted {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            } else {
                mediaPlayer.start()
            }
            _songIsPlayingStateFlow.emit(mediaPlayer.isPlaying)
            updateNotification()
        }
    }

    fun fastRewindPlayer() {
        lifecycleScope.launchWhenStarted {
            val rewindPosition = mediaPlayer.currentPosition - 10000
            val newPosition = if (rewindPosition < 0) 0
            else rewindPosition
            mediaPlayer.seekTo(newPosition)
            _songProgressStateFlow.emit(mediaPlayer.currentPosition)
        }
    }

    fun fastForwardPlayer() {
        lifecycleScope.launchWhenStarted {
            val forwardPosition = mediaPlayer.currentPosition + 10000
            val newPosition = if (forwardPosition > mediaPlayer.duration) mediaPlayer.duration
            else forwardPosition
            mediaPlayer.seekTo(newPosition)
            _songProgressStateFlow.emit(mediaPlayer.currentPosition)
        }
    }

    fun playerSeekTo(millisecond: Int) {
        lifecycleScope.launchWhenStarted {
            mediaPlayer.seekTo(millisecond)
            _songProgressStateFlow.emit(mediaPlayer.currentPosition)
        }
    }
}