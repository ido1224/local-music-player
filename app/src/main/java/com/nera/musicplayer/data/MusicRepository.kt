package com.nera.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.nera.musicplayer.analysis.AudioDecoder
import com.nera.musicplayer.analysis.EnergyAnalyzer
import com.nera.musicplayer.analysis.TempoAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.UUID

private const val TAG = "MusicRepository"

/**
 * Matches the UUID filenames importTracks assigns to copied files. A URI whose display name
 * can't be resolved (e.g. a file:// URI, which DocumentFile.fromSingleUri doesn't handle) falls
 * back to the URI's last path segment, which for one of our own copies is this UUID - not a
 * name any user picked. Never let it stand in as a track title.
 */
private val UUID_FILENAME_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

class MusicRepository(private val context: Context) {

    private val trackDao = AppDatabase.getInstance(context).trackDao()
    private val trackAudioFeaturesDao = AppDatabase.getInstance(context).trackAudioFeaturesDao()

    val tracks: Flow<List<Track>> = trackDao.observeAll()

    fun observeTracksWithFeatures(sortOrder: LibrarySortOrder): Flow<List<TrackWithFeatures>> = when (sortOrder) {
        LibrarySortOrder.DATE_ADDED -> trackDao.observeAllWithFeaturesByDateAdded()
        LibrarySortOrder.BPM -> trackDao.observeAllWithFeaturesByTempo()
        LibrarySortOrder.ENERGY -> trackDao.observeAllWithFeaturesByEnergy()
    }

    /**
     * Copies each picked file into the app's own music folder (Phase 1 is
     * app-managed storage, not a reference to the original external file)
     * and reads its ID3 tags before inserting it into the library.
     */
    suspend fun importTracks(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val musicDir = File(context.filesDir, "music").apply { mkdirs() }

        for (sourceUri in uris) {
            val originalName = DocumentFile.fromSingleUri(context, sourceUri)?.name
                ?: sourceUri.lastPathSegment
                ?: "track"
            val extension = originalName.substringAfterLast('.', "mp3")
            val destFile = File(musicDir, "${UUID.randomUUID()}.$extension")

            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied) continue

            val retriever = MediaMetadataRetriever()
            val track = try {
                retriever.setDataSource(destFile.absolutePath)
                Track(
                    uri = destFile.toURI().toString(),
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?: originalName.substringBeforeLast('.')
                            .takeUnless { UUID_FILENAME_REGEX.matches(it) }
                        ?: "Unknown Track",
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?.take(4)?.toIntOrNull(),
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L,
                    dateAdded = System.currentTimeMillis(),
                    fileSizeBytes = destFile.length()
                )
            } finally {
                retriever.release()
            }

            val trackId = trackDao.insert(track)
            analyzeAudio(trackId, destFile)
        }
    }

    /**
     * Runs once per track at import time (not per-play) and caches the result -
     * see TrackAudioFeatures. Analysis failures are logged and skipped rather
     * than failing the whole import; a track without cached features is a
     * normal state.
     *
     * Tempo and energy use different decode strategies, so they no longer share
     * a single decoded window: tempo needs one continuous stretch (BeatRoot's
     * induction relies on recurring inter-onset intervals - three disjoint
     * snippets would fragment that), while energy specifically samples three
     * short windows spread across the track and averages them, since a single
     * fixed window is too noisy to represent a track's overall intensity (see
     * CLAUDE.md's energy-extraction validation for why).
     */
    private suspend fun analyzeAudio(trackId: Long, file: File) {
        val (bpm, energy) = try {
            val decoded = AudioDecoder.decodeMonoPcm16(file.absolutePath)
            val bpm = decoded?.let { TempoAnalyzer.estimateBpm(it) }
            val energy = EnergyAnalyzer.computeNormalizedRms(file.absolutePath)
            bpm to energy
        } catch (e: Exception) {
            Log.w(TAG, "Audio analysis failed for track $trackId (${file.name})", e)
            return
        }
        trackAudioFeaturesDao.upsert(
            TrackAudioFeatures(trackId = trackId, bpm = bpm, energy = energy, analyzedAt = System.currentTimeMillis())
        )
        Log.d(TAG, "Analyzed track $trackId: bpm=$bpm energy=$energy")
    }

    /**
     * Removes the track from the library only. The `playlist_tracks` foreign
     * key cascades, so it also disappears from every playlist. The underlying
     * file on disk is left untouched.
     */
    suspend fun removeFromLibrary(track: Track) = withContext(Dispatchers.IO) {
        trackDao.delete(track)
    }

    /** Removes the track from the library (see [removeFromLibrary]) and permanently deletes its file. */
    suspend fun deleteTrackFile(track: Track) = withContext(Dispatchers.IO) {
        trackDao.delete(track)
        runCatching { File(URI(track.uri)).delete() }
    }
}
