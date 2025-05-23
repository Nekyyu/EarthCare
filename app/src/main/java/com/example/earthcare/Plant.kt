package com.example.earthcare

data class Plant(
    var id: String? = null,
    var name: String = "",
    var idealTempMin: Float = 18f,
    var idealTempMax: Float = 25f,
    var idealHumidityMin: Float = 40f,
    var idealHumidityMax: Float = 70f,
    var idealLightMin: Float = 5000f,
    var idealLightMax: Float = 15000f
) 