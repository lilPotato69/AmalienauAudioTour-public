package com.example.amalienauaudiotour

data class TourLatLng(val lat: Double, val lng: Double)

data class TourStop(
    val id: String,
    val title: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val audioFile: String
)

data class TourModel(
    val routeName: String,
    val routeGeometry: List<TourLatLng>,
    val stops: List<TourStop>
)