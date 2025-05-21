package com.example.earthcare

import com.google.firebase.database.PropertyName

data class SensorData(
    @PropertyName("Hora") val Hora: String? = null,
    @PropertyName("humdedad_ext") val humdedad_ext: Float? = null,
    val humedad_suelo: Float? = null,
    val luz: Float? = null,
    val porcentaje_humedad_suelo: Float? = null,
    val temperatura_ext: Float? = null
) 