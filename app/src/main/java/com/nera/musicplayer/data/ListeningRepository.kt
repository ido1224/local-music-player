package com.nera.musicplayer.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ListeningRepository"

class ListeningRepository(context: Context) {

    private val listenEventDao = AppDatabase.getInstance(context).listenEventDao()

    suspend fun recordListen(trackId: Long, completionPercent: Int) = withContext(Dispatchers.IO) {
        val event = ListenEvent(
            trackId = trackId,
            timestamp = System.currentTimeMillis(),
            completionPercent = completionPercent
        )
        listenEventDao.insert(event)
        Log.d(TAG, "Recorded listen event: trackId=$trackId completion=$completionPercent%")
    }
}
