package com.nera.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
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

private const val STAGING_PREFS_NAME = "nera_import_staging"
private const val KEY_PROCESSED_STAGING_FILES = "processed_files"

private const val ALBUM_ART_PREFS_NAME = "nera_album_art"
private const val KEY_ALBUM_ART_BACKFILLED = "backfilled_v1"

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
     * A plain top-level folder rather than app-private external storage: on some OEM ROMs (seen
     * on-device) `adb push` can't create directories under Android/data/<pkg>/ even though the
     * app's own runtime access to it is unrestricted - this location is push-able from a PC with
     * no such restriction. Reading it back requires READ_MEDIA_AUDIO (or READ_EXTERNAL_STORAGE
     * pre-13) since it's outside the app's own sandbox; see HomeScreen for the permission request.
     */
    private fun importStagingDir(): File =
        File(Environment.getExternalStorageDirectory(), "NeraMusicImport").apply { mkdirs() }

    /**
     * Bypasses the SAF folder picker entirely: scans [importStagingDir] recursively for audio
     * files dropped there directly (e.g. via `adb push`), and imports each through the same
     * dedup path as the other import flows.
     *
     * Files pushed via `adb`/other tools aren't owned by this app, so scoped storage denies
     * `File.delete()` on them even with READ_MEDIA_AUDIO held (confirmed on-device: reads work,
     * deletes silently return false) - deleting would need a MediaStore user-consent flow, which
     * is exactly the kind of extra prompt this feature exists to avoid. Instead, a SharedPreferences
     * fingerprint set (path+size+lastModified) tracks what's already been handled, so a later
     * rescan only processes genuinely new files without needing write access to old ones.
     */
    suspend fun scanImportFolder(onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportSummary = withContext(Dispatchers.IO) {
        val staging = importStagingDir()
        val allFiles = mutableListOf<File>()
        collectAudioFiles(staging, allFiles)

        val processed = processedStagingFiles()
        val audioFiles = allFiles.filter { fingerprint(it) !in processed }

        val existingKeys = trackDao.getAllTitleArtists().mapTo(mutableSetOf()) { it.dedupeKey() }
        var imported = 0
        var skipped = 0
        audioFiles.forEachIndexed { index, file ->
            if (importOne(Uri.fromFile(file), file.name, existingKeys)) imported++ else skipped++
            processed.add(fingerprint(file))
            file.delete() // best-effort; harmless if scoped storage denies it, see fingerprint set above
            onProgress(index + 1, audioFiles.size)
        }
        saveProcessedStagingFiles(processed)
        ImportSummary(imported, skipped)
    }

    private fun fingerprint(file: File): String = "${file.absolutePath}|${file.length()}|${file.lastModified()}"

    private fun processedStagingFiles(): MutableSet<String> {
        val prefs = context.getSharedPreferences(STAGING_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PROCESSED_STAGING_FILES, emptySet())!!.toMutableSet()
    }

    private fun saveProcessedStagingFiles(processed: Set<String>) {
        context.getSharedPreferences(STAGING_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_PROCESSED_STAGING_FILES, processed).apply()
    }

    private fun collectAudioFiles(dir: File, out: MutableList<File>) {
        dir.listFiles()?.forEach { child ->
            when {
                child.isDirectory -> collectAudioFiles(child, out)
                child.isFile && isAudioFile(child.name) -> out.add(child)
            }
        }
    }

    private fun isAudioFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

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
                fileSizeBytes = destFile.length(),
                albumArtPath = extractAlbumArt(retriever)
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

    private fun albumArtDir(): File = File(context.filesDir, "album_art").apply { mkdirs() }

    /**
     * Saves [retriever]'s embedded picture (if any) as its own file rather than inline in Room -
     * keeps track rows small and lets the art be loaded lazily/cached by an image loader instead
     * of round-tripping through the DB on every recomposition. Returns null (not an error) when
     * the track genuinely has no embedded art, which is a normal, expected state.
     */
    private fun extractAlbumArt(retriever: MediaMetadataRetriever): String? {
        val art = retriever.embeddedPicture ?: return null
        val artFile = File(albumArtDir(), "${UUID.randomUUID()}.jpg")
        return try {
            artFile.writeBytes(art)
            artFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save extracted album art", e)
            null
        }
    }

    /**
     * One-time pass for tracks imported before album-art extraction existed (2026-09-03, added
     * alongside the Track.albumArtPath column) - every import path copies the source file into
     * app storage (see importOne), so it's still sitting at each track's own `uri` and can be
     * re-opened here without re-importing or touching any other field. Gated by a SharedPreferences
     * flag rather than an `albumArtPath IS NULL` re-check on every launch, so a track whose file
     * genuinely has no embedded picture doesn't get re-scanned forever - null is a valid, final
     * outcome for those, not "not yet attempted".
     */
    suspend fun backfillAlbumArtIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(ALBUM_ART_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ALBUM_ART_BACKFILLED, false)) return@withContext

        for (track in trackDao.getTracksMissingAlbumArt()) {
            val file = try { File(URI(track.uri)) } catch (e: Exception) { continue }
            if (!file.exists()) continue

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                extractAlbumArt(retriever)?.let { path -> trackDao.updateAlbumArtPath(track.id, path) }
            } catch (e: Exception) {
                Log.w(TAG, "Album art backfill failed for track ${track.id} (${track.title})", e)
            } finally {
                retriever.release()
            }
        }
        prefs.edit().putBoolean(KEY_ALBUM_ART_BACKFILLED, true).apply()
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
