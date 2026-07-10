package com.example.amalienauaudiotour

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader


fun loadTourDataFromAssets(context: Context): TourModel {
    val inputStream = context.assets.open("tour_data.json")
    val reader = BufferedReader(InputStreamReader(inputStream))
    val jsonString = reader.use { it.readText() }

    val rootJsonObject = JSONObject(jsonString)
    val routeName = rootJsonObject.getString("routeName")

    val stopsArray = rootJsonObject.getJSONArray("stops")
    val stops = mutableListOf<TourStop>()
    for (i in 0 until stopsArray.length()) {
        val item = stopsArray.getJSONObject(i)
        stops.add(
            TourStop(
                id = item.getString("id"),
                title = item.getString("title"),
                description = item.getString("description"),
                lat = item.getDouble("lat"),
                lng = item.getDouble("lng"),
                audioFile = item.getString("audioFile")
            )
        )
    }

    return TourModel(routeName, emptyList(), stops)
}

fun loadGeoJsonRouteFromAssets(context: Context): List<TourLatLng> {
    try {
        // 1. Считываем сырой текст из файла .geojson с координатами маршрута
        val inputStream = context.assets.open("route.geojson")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = reader.use { it.readText() }

        val root = JSONObject(jsonString)
        val features = root.getJSONArray("features")
        if (features.length() == 0) return emptyList()

        // 2. Парсим стандартную матрицу координат GeoJSON
        val firstFeature = features.getJSONObject(0)
        val geometry = firstFeature.getJSONObject("geometry")
        val coordinates = geometry.getJSONArray("coordinates")

        val points = mutableListOf<TourLatLng>()
        for (i in 0 until coordinates.length()) {
            val coordArray = coordinates.getJSONArray(i)
            val lng = coordArray.getDouble(0)
            val lat = coordArray.getDouble(1)
            points.add(TourLatLng(lat, lng))
        }
        return points
    } catch (e: Exception) {
        e.printStackTrace()
        return emptyList()
    }
}