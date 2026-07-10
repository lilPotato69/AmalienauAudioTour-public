package com.example.amalienauaudiotour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
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

/** Рисует круглую пронумерованную иконку маркера точки маршрута. */
fun createNumberedMarkerIcon(context: Context, number: Int, isSelected: Boolean): Drawable {
    val size = 90
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)

    val pinPaint = Paint().apply {
        color = if (isSelected) "#007AFF".toColorInt() else "#E28743".toColorInt()
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val borderPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    val textPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val centerX = size / 2f
    val centerY = size / 2f
    val radius = size / 2.4f

    canvas.drawCircle(centerX, centerY, radius, pinPaint)
    canvas.drawCircle(centerX, centerY, radius, borderPaint)

    val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(number.toString(), centerX, textY, textPaint)

    return bitmap.toDrawable(context.resources)
}

/**
 * Держит ссылку на нижележащий osmdroid MapView, чтобы внешний UI (например, кнопка "🎯")
 * мог им управлять, не зная о типах osmdroid напрямую.
 */
class TourMapState {
    internal var mapView: MapView? = null

    fun centerOnUserLocation() {
        val overlay = mapView?.overlays?.filterIsInstance<MyLocationNewOverlay>()?.firstOrNull()
        val userLocation = overlay?.myLocation ?: return
        mapView?.controller?.animateTo(userLocation)
    }
}

@Composable
fun rememberTourMapState(): TourMapState = remember { TourMapState() }

/**
 * osmdroid MapView, обёрнутый под Compose. Вся возня с overlays (маршрут, метки точек,
 * геолокация пользователя) спрятана здесь — снаружи знают только про модель тура,
 * выбранную точку и разрешение на геолокацию.
 */
@Composable
fun TourMapView(
    modifier: Modifier = Modifier,
    mapState: TourMapState,
    tourModel: TourModel,
    selectedStop: TourStop?,
    locationPermissionGranted: Boolean,
    onStopSelected: (TourStop) -> Unit
) {
    // Останавливаем карту при закрытии экрана. audioPlayer здесь не освобождаем —
    // этим занимается TourViewModel.onCleared(), которая переживает поворот экрана
    // и срабатывает только при реальном уничтожении экрана.
    DisposableEffect(Unit) {
        onDispose { mapState.mapView?.onDetach() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val sharedPref = ctx.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
            Configuration.getInstance().load(ctx, sharedPref)
            Configuration.getInstance().osmdroidBasePath = File(ctx.filesDir, "osmdroid")
            Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.2)
                if (tourModel.stops.isNotEmpty()) {
                    controller.setCenter(GeoPoint(tourModel.stops.first().lat, tourModel.stops.first().lng))
                }
                mapState.mapView = this
            }
        },
        update = { mapView ->
            // Заводим слой локации пользователя один раз, чтобы не плодить его на каждой рекомпозиции
            val myLocationOverlay = mapView.ensureMyLocationOverlay(locationPermissionGranted)

            // Сбрасываем путь и метки, чтобы избежать проблем при перерисовке слоёв
            mapView.clearRouteAndMarkerOverlays()
            myLocationOverlay?.let { mapView.overlays.remove(it) }

            // Слой 1 (нижний): линия маршрута
            mapView.addRouteOverlay(tourModel.routeGeometry, isHighlighted = selectedStop != null)

            // Слой 2: метки исторических точек
            mapView.addStopMarkers(tourModel.stops, selectedStop, onStopSelected)

            // Слой 3 (верхний): маркер локации пользователя
            myLocationOverlay?.let { mapView.overlays.add(it) }

            mapView.invalidate()
        }
    )
}

private fun MapView.ensureMyLocationOverlay(locationPermissionGranted: Boolean): MyLocationNewOverlay? {
    val existing = overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
    if (existing != null) return existing
    if (!locationPermissionGranted) return null

    return MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
        enableMyLocation()
        setPersonAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    }
}

private fun MapView.clearRouteAndMarkerOverlays() {
    overlays.removeAll(overlays.filterIsInstance<Polyline>())
    overlays.removeAll(overlays.filterIsInstance<Marker>())
}

private fun MapView.addRouteOverlay(points: List<TourLatLng>, isHighlighted: Boolean) {
    if (points.isEmpty()) return

    val routeColor = if (isHighlighted) "#007AFF".toColorInt() else "#4A5568".toColorInt()

    val polyline = Polyline().apply {
        setPoints(points.map { GeoPoint(it.lat, it.lng) })

        outlinePaint.color = routeColor
        outlinePaint.strokeWidth = 12f
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.strokeCap = Paint.Cap.ROUND

        val arrowPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val chevronPath = Path().apply {
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
        setMilestoneManagers(mutableListOf(MilestoneManager(MilestoneMeterDistanceLister(55.0), displayer)))
    }
    overlays.add(polyline)
}

private fun MapView.addStopMarkers(
    stops: List<TourStop>,
    selectedStop: TourStop?,
    onStopSelected: (TourStop) -> Unit
) {
    stops.forEachIndexed { index, stop ->
        val isCurrentStop = selectedStop?.id == stop.id
        val marker = Marker(this).apply {
            position = GeoPoint(stop.lat, stop.lng)
            title = stop.title
            icon = createNumberedMarkerIcon(context, index + 1, isCurrentStop)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ ->
                onStopSelected(stop)
                true
            }
        }
        overlays.add(marker)
    }
}
