package com.example.amalienauaudiotour

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.milestones.MilestoneManager
import org.osmdroid.views.overlay.milestones.MilestoneMeterDistanceLister
import org.osmdroid.views.overlay.milestones.MilestonePathDisplayer
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import androidx.core.graphics.toColorInt

import androidx.lifecycle.viewmodel.compose.viewModel



@Composable
fun TourScreenSkeleton(viewModel: TourViewModel = viewModel()) {
    val context = LocalContext.current
    val audioPlayer = viewModel.audioPlayer

    // Отслеживаем точку отсчёта для плавающей кнопки
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Останавливаем карту при закрытии экрана.
    // Сам audioPlayer здесь больше не освобождаем — этим занимается TourViewModel.onCleared(),
    // которая переживает поворот экрана и срабатывает только при реальном уничтожении экрана
    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }

    // 1. Разрешения на геоданные
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Авто-запрос на выдачу разрешения сразу при загрузке
    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Данные тура грузятся асинхронно во ViewModel — пока они не готовы, карту не строим.
    // Раньше tourData.stops.first() читался прямо во время создания MapView синхронно из assets;
    // теперь ждём загрузки явно, иначе на первом кадре tourModel будет null
    val tourModel = viewModel.tourModel
    if (viewModel.isLoading || tourModel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val staticRoutePoints = tourModel.routeGeometry
    val selectedStop = viewModel.selectedStop

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val sharedPref = ctx.getSharedPreferences("osmdroid_prefs", android.content.Context.MODE_PRIVATE)
                Configuration.getInstance().load(ctx, sharedPref)
                Configuration.getInstance().osmdroidBasePath = File(ctx.filesDir, "osmdroid")
                Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")

                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)

                    controller.setZoom(18)
                    if (tourModel.stops.isNotEmpty()) {
                        controller.setCenter(GeoPoint(tourModel.stops.first().lat, tourModel.stops.first().lng))
                    }
                    mapViewRef = this
                }
            },
            update = { mapView ->
                // Ищем существующий слой маркера локации пользователя для предотвращения утечек памяти
                var myLocationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()

                // Если дано разрешение и слой не существует, рисуем его ЕДИНОЖДЫ
                if (locationPermissionGranted && myLocationOverlay == null) {
                    myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(mapView.context), mapView).apply {
                        enableMyLocation() // Заставляем железо отрисовывать маркер локации
                        setPersonAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                }

                // Сбрасываем путь и метки, чтобы избежать проблемы при отрисовке слоёв
                val existingLines = mapView.overlays.filterIsInstance<Polyline>()
                val existingMarkers = mapView.overlays.filterIsInstance<Marker>()
                mapView.overlays.removeAll(existingLines)
                mapView.overlays.removeAll(existingMarkers)

                if (myLocationOverlay != null) {
                    mapView.overlays.remove(myLocationOverlay)
                }

                // Слой 1 (Нижний): Линия маршрута
                if (staticRoutePoints.isNotEmpty()) {
                    val routeColor = if (selectedStop != null) {
                        "#007AFF".toColorInt()
                    } else {
                        "#4A5568".toColorInt()
                    }

                    val polyline = Polyline().apply {
                        val points = staticRoutePoints.map { GeoPoint(it.lat, it.lng) }
                        setPoints(points)

                        outlinePaint.color = routeColor
                        outlinePaint.strokeWidth = 12f
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND

                        val arrowPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.FILL
                        }

                        val chevronPath = android.graphics.Path().apply {
                            moveTo(-5f, -6f)
                            lineTo(7f, 0f)
                            lineTo(-5f, 6f)
                            close()
                        }

                        val displayer = MilestonePathDisplayer(0.0, true, chevronPath, arrowPaint)

                        // Эдакая зараза. Джава момент. 2 часа логи строчил пока не поймал.
                        // mutableListOf, а не listOf: тупой старый osmdroid мутирует этот список внутри себя (например,
                        // при пересоздании View на повороте экрана) — на immutable-списке это кидало
                        // UnsupportedOperationException
                        val managers = mutableListOf(
                            MilestoneManager(MilestoneMeterDistanceLister(55.0), displayer)
                        )
                        setMilestoneManagers(managers)
                    }
                    mapView.overlays.add(polyline)
                }

                // Слой 2: Метки исторических точек
                tourModel.stops.forEachIndexed { index, stop ->
                    val isCurrentStop = selectedStop?.id == stop.id
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(stop.lat, stop.lng)
                        title = stop.title
                        icon = createNumberedMarkerIcon(mapView.context, index + 1, isCurrentStop)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                        setOnMarkerClickListener { _, _ ->
                            viewModel.selectStop(stop)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                // Слой 3: Маркер локации пользователя
                if (myLocationOverlay != null) {
                    mapView.overlays.add(myLocationOverlay)
                }

                mapView.invalidate()
            }
        )

        // 2. Кнопка центровки на местоположении пользователя
        if (locationPermissionGranted) {
            FloatingActionButton(
                onClick = {
                    val overlay = mapViewRef?.overlays?.filterIsInstance<MyLocationNewOverlay>()?.firstOrNull()
                    val userLocation = overlay?.myLocation
                    if (userLocation != null) {
                        mapViewRef?.controller?.animateTo(userLocation)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Кнопка всегда немного над контрольной панелью плеера
                    .padding(bottom = 250.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text("🎯", style = MaterialTheme.typography.titleLarge)
            }
        }

        // 3. Контрольная панель плеера (динамический масштаб)
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                val titleText = selectedStop?.title ?: "Введение"
                val descText = selectedStop?.description ?: "Добро пожаловать в Амалиенау! Выберите точку на карте или нажмите «СЛУШАТЬ ВВЕДЕНИЕ»."
                val targetAudioFile = selectedStop?.audioFile ?: "0. презентация.opus"

                // Проверяем, играет ли конкретно текущий трек
                val isThisTrackPlaying = audioPlayer.isPlaying && audioPlayer.currentFile == targetAudioFile

                Text(text = titleText, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
                Text(text = descText, style = MaterialTheme.typography.bodyMedium)

                Button(
                    onClick = {
                        viewModel.togglePlay(targetAudioFile)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    // Динамический текст в соответствии с состоянием плеера
                    val buttonText = when {
                        isThisTrackPlaying -> "⏸ ПАУЗА"
                        selectedStop != null -> "▶️ ВОСПРОИЗВЕСТИ АУДИО"
                        else -> "🎧 СЛУШАТЬ ВВЕДЕНИЕ"
                    }
                    Text(text = buttonText)
                }
            }
        }
    }
}