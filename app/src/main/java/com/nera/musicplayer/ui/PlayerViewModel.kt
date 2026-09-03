package com.nera.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nera.musicplayer.data.AppDatabase
import com.nera.musicplayer.data.ListeningRepository
import com.nera.musicplayer.data.Track
import com.nera.musicplayer.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.io.File

private const val LISTEN_COMPLETE_THRESHOLD = 0.8f

data class PlayerUiState(
    val trackTitle: String? = null,
    val artist: String? = null,
    val albumArtUri: Uri? = null,
    /** Cached tempo for the current track, looked up by mediaId once analysis exists for it. Null while unknown/unanalyzed. */
    val bpm: Float? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private val listeningRepository = ListeningRepository(application)
    private val trackAudioFeaturesDao = AppDatabase.getInstance(application).trackAudioFeaturesDao()

    private var controller: MediaController? = null

    /** mediaId (Track.id as string) of the item we've already logged a listen event for, so it's only recorded once per playback instance. */
    private var loggedListenForMediaId: String? = null

    init {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        viewModelScope.launch {
            val c = MediaController.Builder(context, sessionToken).buildAsync().await()
            controller = c
            _uiState.value = _uiState.value.copy(
                trackTitle = c.currentMediaItem?.mediaMetadata?.title?.toString(),
                artist = c.currentMediaItem?.mediaMetadata?.artist?.toString(),
                albumArtUri = c.currentMediaItem?.mediaMetadata?.artworkUri,
                isPlaying = c.isPlaying,
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode
            )
            updateBpm(c.currentMediaItem?.mediaId)
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    loggedListenForMediaId = null
                    _uiState.value = _uiState.value.copy(
                        trackTitle = mediaItem?.mediaMetadata?.title?.toString(),
                        artist = mediaItem?.mediaMetadata?.artist?.toString(),
                        albumArtUri = mediaItem?.mediaMetadata?.artworkUri,
                        durationMs = 0L
                    )
                    updateBpm(mediaItem?.mediaId)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _uiState.value = _uiState.value.copy(shuffleEnabled = shuffleModeEnabled)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
                }
            })
            startPositionTicker()
        }
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null) {
                    val positionMs = c.currentPosition.coerceAtLeast(0L)
                    val durationMs = c.duration.coerceAtLeast(0L)
                    _uiState.value = _uiState.value.copy(positionMs = positionMs, durationMs = durationMs)
                    maybeRecordListen(c, positionMs, durationMs)
                }
                delay(500)
            }
        }
    }

    /** Looks up the cached tempo for [mediaId] (a Track.id) and updates uiState.bpm once it resolves. */
    private fun updateBpm(mediaId: String?) {
        val trackId = mediaId?.toLongOrNull()
        if (trackId == null) {
            _uiState.value = _uiState.value.copy(bpm = null)
            return
        }
        viewModelScope.launch {
            val bpm = trackAudioFeaturesDao.getForTrack(trackId)?.bpm
            _uiState.value = _uiState.value.copy(bpm = bpm)
        }
    }

    private fun maybeRecordListen(controller: MediaController, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val mediaId = controller.currentMediaItem?.mediaId ?: return
        if (mediaId == loggedListenForMediaId) return

        val completionFraction = positionMs.toFloat() / durationMs.toFloat()
        if (completionFraction < LISTEN_COMPLETE_THRESHOLD) return

        val trackId = mediaId.toLongOrNull() ?: return
        loggedListenForMediaId = mediaId
        val completionPercent = (completionFraction * 100).toInt().coerceIn(0, 100)
        viewModelScope.launch {
            listeningRepository.recordListen(trackId, completionPercent)
        }
    }

    /** Loads [tracks] as the play queue and starts playback at [startIndex]. */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val mediaItems = tracks.map { it.toMediaItem() }
        controller?.apply {
            setMediaItems(mediaItems, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** Cycles OFF -> ALL -> ONE -> OFF. */
    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}

private fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(uri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .apply { albumArtPath?.let { setArtworkUri(Uri.fromFile(File(it))) } }
                .build()
        )
        .build()
