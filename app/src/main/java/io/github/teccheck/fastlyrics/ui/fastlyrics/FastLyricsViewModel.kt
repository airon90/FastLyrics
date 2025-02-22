package io.github.teccheck.fastlyrics.ui.fastlyrics

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.Success
import io.github.teccheck.fastlyrics.api.LyricsApi
import io.github.teccheck.fastlyrics.api.MediaSession
import io.github.teccheck.fastlyrics.exceptions.LyricsApiException
import io.github.teccheck.fastlyrics.model.SongMeta
import io.github.teccheck.fastlyrics.model.SongWithLyrics
import io.github.teccheck.fastlyrics.service.DummyNotificationListenerService

class FastLyricsViewModel : ViewModel() {

    private val _songMeta = MutableLiveData<Result<SongMeta, LyricsApiException>>()
    private val _songWithLyrics = MutableLiveData<Result<SongWithLyrics, LyricsApiException>>()

    val songMeta: LiveData<Result<SongMeta, LyricsApiException>> = _songMeta
    val songWithLyrics: LiveData<Result<SongWithLyrics, LyricsApiException>> = _songWithLyrics

    var autoRefresh = false

    private val songMetaCallback = object : MediaSession.SongMetaCallback() {
        var currentContext: Context? = null

        override fun onSongMetaChanged(songMeta: SongMeta) {
            if (!autoRefresh) return

            _songMeta.postValue(Success(songMeta))
            LyricsApi.getLyricsAsync(songMeta, _songWithLyrics)
        }
    }

    fun loadLyricsForCurrentSong(context: Context): Boolean {
        if (!DummyNotificationListenerService.canAccessNotifications(context)) {
            Log.w(TAG, "Can't access notifications")
            return false
        }

        val songMetaResult = MediaSession.getSongInformation(context)
        _songMeta.value = songMetaResult

        if (songMetaResult is Success) {
            LyricsApi.getLyricsAsync(songMetaResult.value, _songWithLyrics)
        }

        return songMetaResult is Success
    }

    fun setupSongMetaListener(context: Context) {
        songMetaCallback.currentContext = context
        MediaSession.registerSongMetaCallback(context, songMetaCallback)
    }

    override fun onCleared() {
        super.onCleared()
        songMetaCallback.currentContext?.let {
            MediaSession.unregisterSongMetaCallback(it, songMetaCallback)
        }
    }

    companion object {
        private const val TAG = "FastLyricsViewModel"
    }
}