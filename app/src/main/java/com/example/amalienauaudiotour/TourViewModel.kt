package com.example.amalienauaudiotour

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TourViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    val audioPlayer = AudioTourPlayer(context)

    var isLoading by mutableStateOf(true)
        private set

    var tourModel by mutableStateOf<TourModel?>(null)
        private set

    var selectedStop by mutableStateOf<TourStop?>(null)
        private set

    init {
        loadTourData()
    }

    private fun loadTourData() {
        viewModelScope.launch {
            isLoading = true

            // Уходим с Главного потока на IO-поток для чтения файлов
            val loadedModel = withContext(Dispatchers.IO) {
                val baseModel = loadTourDataFromAssets(context)
                val geometry = loadGeoJsonRouteFromAssets(context)

                // Объединяем данные в одну модель
                baseModel.copy(routeGeometry = geometry)
            }

            tourModel = loadedModel
            isLoading = false
        }
    }

    fun selectStop(stop: TourStop?) {
        selectedStop = stop
    }

    fun togglePlay(fileName: String) {
        audioPlayer.togglePlay(fileName)
    }

    // Метод очистки ресурсов, когда ViewModel уничтожается (например, при выходе из Activity)
    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}