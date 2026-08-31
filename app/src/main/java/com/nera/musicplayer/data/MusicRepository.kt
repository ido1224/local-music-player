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

private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "flac", "ogg", "aac", "wma")

class MusicRepository(private val context: Context) {

    private val trackDao = AppDatabase.getInstance(context).trackDao()
    private val trackAudioFeaturesDao = AppDatabase.getInstance(context).trackAudioFeaturesDao()

    val tracks: Flow<List<Track>> = trackDao.observeAll()

    fun observeTracksWithFeatures(sortOrder: LibrarySortOrder): Flow<List<TrackWithFeatures>> = when (sortOrder) {
        LibrarySortOrder.DATE_ADDED -> trackDao.observeAllWithFeaturesByDateAdded()
        LibrarySortOrder.BPM -> trackDao.observeAllWithFeaturesByTempo()
        LibrarySortOrder.ENERGY -> trackDao.observeAllWithFeaturesByEnergy()
        LibrarySortOrder.BY_NAME -> trackDao.observeAllWithFeaturesByTitle()
    }

    /**
     * Copies each picked file into the app's own music folder (Phase 1 is
     * app-managed storage, not a reference to the original external file)
     * and reads its ID3 tags before inserting it into the library.
     */
    suspend fun importTracks(uris: List<Uri>): ImportSummary = withContext(Dispatchers.IO) {
        val existingKeys = trackDao.getAllTitleArtists().mapTo(mutableSetOf()) { it.dedupeKey() }
        var imported = 0
        var skipped = 0
        for (sourceUri in uris) {
            val originalName = DocumentFile.fromSingleUri(context, sourceUri)?.name
                ?: sourceUri.lastPathSegment
            if (importOne(sourceUri, originalName, existingKeys)) imported++ else skipped++
        }
        ImportSummary(imported, skipped)
    }

    /**
     * Recursively scans [treeUri] (picked via SAF's OpenDocumentTree) for audio files in any
     * subfolder and imports everything found in one pass - e.g. a MusicBrainz Picard-style
     * Artist/Album/Track.mp3 tree. [onProgress] fires after each file (current, total) so the
     * caller can drive a determinate progress indicator across what can be 100+ files.
     */
    suspend fun importFromTree(treeUri: Uri, onProgress: (Int, Int) -> Unit): ImportSummary = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        val audioFiles = mutableListOf<DocumentFile>()
        if (root != null) collectAudioFiles(root, audioFiles)

        val existingKeys = trackDao.getAllTitleArtists().mapTo(mutableSetOf()) { it.dedupeKey() }
        var imported = 0
        var skipped = 0
        audioFiles.forEachIndexed { index, file ->
            if (importOne(file.uri, file.name, existingKeys)) imported++ else skipped++
            onProgress(index + 1, audioFiles.size)
        }
        ImportSummary(imported, skipped)
    }

    private fun collectAudioFiles(dir: DocumentFile, out: MutableList<DocumentFile>) {
        for (child in dir.listFiles()) {
            when {
                child.isDirectory -> collectAudioFiles(child, out)
                child.isFile && isAudioFile(child) -> out.add(child)
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        if (file.type?.startsWith("audio/") == true) return true
        val extension = file.name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return extension in AUDIO_EXTENSIONS
    }

    /**
     * Copies [sourceUri] into app storage, reads its tags, and inserts it - unless a track with
     * the same title+artist is already in [existingKeys] (checked case-insensitively), in which
     * case the copy is discarded and this returns false. Title+artist rather than a file hash:
     * it catches the case that actually annoys a user (the same song appearing twice, however it
     * got re-encoded/re-tagged/re-named along the way), which a byte-exact hash would miss.
     */
    private suspend fun importOne(sourceUri: Uri, originalName: String?, existingKeys: MutableSet<String>): Boolean {
        val musicDir = File(context.filesDir, "music").apply { mkdirs() }
        val name = originalName ?: "track"
        val extension = name.substringAfterLast('.', "mp3")
        val destFile = File(musicDir, "${UUID.randomUUID()}.$extension")

        val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (!copied) return false

        val retriever = MediaMetadataRetriever()
        val track = try {
            retriever.setDataSource(destFile.absolutePath)
            Track(
                uri = destFile.toURI().toString(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: name.substringBeforeLast('.')
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

        val key = TrackTitleArtist(track.title, track.artist).dedupeKey()
        if (key in existingKeys) {
            destFile.delete()
            return false
        }
        existingKeys.add(key)

        val trackId = trackDao.insert(track)
        analyzeAudio(trackId, destFile)
        return true
    }

    private fun TrackTitleArtist.dedupeKey(): String =
        "${title.trim().lowercase()}|${artist?.trim()?.lowercase().orEmpty()}"

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
