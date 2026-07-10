package com.example.amalienauaudiotour

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AudioTourPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    var currentFile: String? = null
        private set

    var isPlaying by mutableStateOf(false)
        private set

    // Идёт асинхронная подготовка (между prepareAsync() и onPrepared) — плеер ещё нельзя стартовать
    private var isPreparing = false

    fun togglePlay(fileName: String) {
        // Игнорируем повторный этап по тому же треку, пока он ещё готовится:
        // без этой проверки второй тап попадал в ветку play() -> else -> mediaPlayer?.start()
        // на ещё не подготовленном MediaPlayer, что кидает IllegalStateException
        if (isPreparing && currentFile == fileName) return

        if (isPlaying && currentFile == fileName) {
            pause()
        } else {
            play(fileName)
        }
    }

    private fun play(fileName: String) {
        if (currentFile != fileName) {
            release()
            currentFile = fileName
            isPreparing = true
            try {
                mediaPlayer = MediaPlayer().apply {
                    val descriptor = context.assets.openFd("audio/$fileName")
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    descriptor.close()

                    setOnPreparedListener {
                        this@AudioTourPlayer.isPreparing = false
                        start()
                        this@AudioTourPlayer.isPlaying = true
                    }
                    setOnCompletionListener {
                        this@AudioTourPlayer.isPlaying = false
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("AudioTourPlayer", "MediaPlayer error: what=$what extra=$extra")
                        // Полностью сбрасываем состояние, чтобы следующий тап начинал с чистого листа,
                        // а не пытался стартовать плеер, застрявший в состоянии ошибки
                        this@AudioTourPlayer.release()
                        true
                    }
                    // готовим плеер асинхронно (запускаем подготовку последней, когда все листенеры уже навешаны)
                    prepareAsync()
                }
            } catch (e: Exception) {
                isPreparing = false
                currentFile = null
                e.printStackTrace()
            }
        } else {
            // Тот же файл, но не играет (после паузы) — плеер уже подготовлен ранее, просто продолжаем
            try {
                mediaPlayer?.start()
                isPlaying = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
    }

    fun release() {
        mediaPlayer?.apply {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            release()
        }
        mediaPlayer = null
        isPlaying = false
        isPreparing = false
        currentFile = null
    }
}