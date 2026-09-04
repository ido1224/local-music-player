package com.nera.musicplayer.update

import com.nera.musicplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GITHUB_LATEST_RELEASE_API_URL =
    "https://api.github.com/repos/ido1224/local-music-player/releases/latest"

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val versionName: String, val releaseUrl: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Checks GitHub Releases for a newer tagged version than the one currently installed. Uses plain
 * HttpURLConnection + org.json (both already in the platform) rather than adding OkHttp/Retrofit
 * for what's a single unauthenticated GET - matches this project's general "don't add a second
 * library for something this small" pattern (see TarsosDSP/MediaMetadataRetriever notes elsewhere
 * in CLAUDE.md).
 */
object UpdateChecker {

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(GITHUB_LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Error("GitHub API returned HTTP $responseCode")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.getString("tag_name") // e.g. "v1.0"
            val releaseUrl = json.getString("html_url")
            val latestVersionName = tagName.removePrefix("v")

            if (isNewerVersion(latestVersionName, BuildConfig.VERSION_NAME)) {
                UpdateCheckResult.UpdateAvailable(latestVersionName, releaseUrl)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Compares dot-separated numeric version strings component-by-component (so "1.10" correctly
     * reads as newer than "1.9", unlike a plain lexicographic string comparison). A missing or
     * non-numeric component is treated as 0.
     */
    internal fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".")
        val currentParts = current.split(".")
        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val remotePart = remoteParts.getOrNull(i)?.toIntOrNull() ?: 0
            val currentPart = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (remotePart != currentPart) return remotePart > currentPart
        }
        return false
    }
}
