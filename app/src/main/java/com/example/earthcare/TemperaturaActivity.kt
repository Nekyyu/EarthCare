package com.example.earthcare

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.animation.Easing
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.*

class TemperaturaActivity : AppCompatActivity() {

    private lateinit var chartTemperatura: LineChart
    private lateinit var textViewMaxTemperaturaValue: TextView
    private lateinit var textViewMinTemperaturaValue: TextView
    private lateinit var textViewPrediccionTemperaturaValue: TextView
    private lateinit var imageButtonBackTemperatura: ImageButton

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference
    private lateinit var sensorDataRef: DatabaseReference
    private var currentPlantId: String = ""
    private var idealTempMin: Float = 18f
    private var idealTempMax: Float = 25f

    private lateinit var currentPlantListener: ValueEventListener
    private lateinit var sensorDataListener: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_temperatura)

        // Inicializar elementos de UI
        chartTemperatura = findViewById(R.id.chartTemperatura)
        textViewMaxTemperaturaValue = findViewById(R.id.textViewMaxTemperaturaValue)
        textViewMinTemperaturaValue = findViewById(R.id.textViewMinTemperaturaValue)
        textViewPrediccionTemperaturaValue = findViewById(R.id.textViewPrediccionTemperaturaValue)
        imageButtonBackTemperatura = findViewById(R.id.imageButtonBackTemperatura)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        val currentUser = auth.currentUser ?: run {
            finish()
            return
        }

        userRef = database.getReference("users").child(currentUser.uid)
        sensorDataRef = database.getReference("sensorData").child(currentUser.uid)

        // Configurar gráfica
        configureChart(chartTemperatura)

        // Configurar listeners
        setupFirebaseListeners()

        imageButtonBackTemperatura.setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupFirebaseListeners() {
        // Listener para cambios en la planta actual
        currentPlantListener = userRef.child("currentPlant").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newPlantId = snapshot.getValue(String::class.java) ?: ""
                if (newPlantId != currentPlantId) {
                    currentPlantId = newPlantId
                    if (currentPlantId.isNotEmpty()) {
                        getIdealTemperatures()
                        readFirebaseData()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@TemperaturaActivity, "Error al obtener planta actual", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getIdealTemperatures() {
        if (currentPlantId.isEmpty()) return

        userRef.child("plants").child(currentPlantId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Obtener valores con manejo robusto de diferentes tipos y valores por defecto
                idealTempMin = when {
                    snapshot.hasChild("idealTempMin") -> when (val temp = snapshot.child("idealTempMin").value) {
                        is Long -> temp.toFloat()
                        is Double -> temp.toFloat()
                        is Int -> temp.toFloat()
                        is Float -> temp
                        else -> 18f
                    }
                    snapshot.hasChild("idealTemp") -> when (val temp = snapshot.child("idealTemp").value) {
                        is Long -> temp.toFloat() - 5f
                        is Double -> temp.toFloat() - 5f
                        is Int -> temp.toFloat() - 5f
                        is Float -> temp - 5f
                        else -> 18f
                    }
                    else -> 18f
                }

                idealTempMax = when {
                    snapshot.hasChild("idealTempMax") -> when (val temp = snapshot.child("idealTempMax").value) {
                        is Long -> temp.toFloat()
                        is Double -> temp.toFloat()
                        is Int -> temp.toFloat()
                        is Float -> temp
                        else -> 25f
                    }
                    snapshot.hasChild("idealTemp") -> when (val temp = snapshot.child("idealTemp").value) {
                        is Long -> temp.toFloat() + 5f
                        is Double -> temp.toFloat() + 5f
                        is Int -> temp.toFloat() + 5f
                        is Float -> temp + 5f
                        else -> 25f
                    }
                    else -> 25f
                }

                updateIdealTemperatureLines()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@TemperaturaActivity, "Error al obtener temperaturas ideales", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun configureChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(true)
        chart.setBackgroundColor(Color.WHITE)

        // Configurar eje X
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.LTGRAY
        xAxis.gridLineWidth = 0.5f
        xAxis.setDrawAxisLine(true)
        xAxis.textColor = Color.DKGRAY
        xAxis.textSize = 10f
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.labelRotationAngle = -45f
        xAxis.granularity = 1f

        // Configurar eje Y izquierdo
        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.DKGRAY
        leftAxis.textSize = 10f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.gridLineWidth = 0.5f
        leftAxis.setDrawAxisLine(true)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 40f
        leftAxis.setDrawZeroLine(true)
        leftAxis.zeroLineColor = Color.GRAY
        leftAxis.zeroLineWidth = 1f

        // Configurar eje Y derecho
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        // Configurar leyenda
        chart.legend.textColor = Color.DKGRAY
        chart.legend.textSize = 12f
        chart.legend.isEnabled = true
        chart.legend.formSize = 12f
        chart.legend.formLineWidth = 2f
        chart.legend.form = Legend.LegendForm.LINE

        // Configurar marcador
        chart.setDrawMarkers(true)
        chart.marker = CustomMarkerView(this, R.layout.custom_marker_view, "°C")

        // Animación
        chart.animateX(1500, Easing.EaseInOutQuart)
    }

    private fun updateIdealTemperatureLines() {
        val leftAxis = chartTemperatura.axisLeft
        leftAxis.removeAllLimitLines()

        // Solo añadir líneas si los valores son válidos
        if (idealTempMin > 0 && idealTempMax > idealTempMin) {
            // Línea de temperatura mínima ideal
            val minLimitLine = LimitLine(idealTempMin, "Mín: ${idealTempMin.roundToInt()}°C")
            minLimitLine.lineWidth = 1.5f
            minLimitLine.lineColor = Color.rgb(76, 175, 80)
            minLimitLine.textColor = Color.rgb(76, 175, 80)
            minLimitLine.textSize = 10f
            minLimitLine.enableDashedLine(10f, 10f, 0f)

            // Línea de temperatura máxima ideal
            val maxLimitLine = LimitLine(idealTempMax, "Máx: ${idealTempMax.roundToInt()}°C")
            maxLimitLine.lineWidth = 1.5f
            maxLimitLine.lineColor = Color.rgb(76, 175, 80)
            maxLimitLine.textColor = Color.rgb(76, 175, 80)
            maxLimitLine.textSize = 10f
            maxLimitLine.enableDashedLine(10f, 10f, 0f)

            leftAxis.addLimitLine(minLimitLine)
            leftAxis.addLimitLine(maxLimitLine)
        }

        chartTemperatura.invalidate()
    }

    private fun readFirebaseData() {
        // Remover listener anterior si existe
        if (::sensorDataListener.isInitialized) {
            sensorDataRef.child(currentPlantId).child("history").removeEventListener(sensorDataListener)
        }

        if (currentPlantId.isEmpty()) return

        sensorDataListener = sensorDataRef.child(currentPlantId).child("history")
            .orderByChild("timestamp")
            .limitToLast(24)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val entries = mutableListOf<Entry>()
                    val timestamps = mutableListOf<String>()
                    var maxTemperatura = Float.NEGATIVE_INFINITY
                    var minTemperatura = Float.POSITIVE_INFINITY

                    snapshot.children.forEachIndexed { index, dataSnapshot ->
                        val sensorData = dataSnapshot.getValue(SensorData::class.java)
                        sensorData?.let {
                            val timestamp = it.Hora ?: ""
                            timestamps.add(timestamp)

                            it.temperatura_ext?.let { tempValue ->
                                entries.add(Entry(index.toFloat(), tempValue))
                                if (tempValue > maxTemperatura) maxTemperatura = tempValue
                                if (tempValue < minTemperatura) minTemperatura = tempValue
                            }
                        }
                    }

                    if (entries.isNotEmpty()) {
                        updateChart(entries, timestamps)
                        updateMinMax(maxTemperatura, minTemperatura)
                        val prediccion = calcularPrediccion(entries)
                        textViewPrediccionTemperaturaValue.text = String.format("%.1f°C", prediccion)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@TemperaturaActivity, "Error al leer datos", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun calcularPrediccion(entries: List<Entry>): Float {
        if (entries.size < 2) return entries.firstOrNull()?.y ?: 0f

        var sumX = 0f
        var sumY = 0f
        var sumXY = 0f
        var sumX2 = 0f
        val n = entries.size.toFloat()

        entries.forEach { entry ->
            sumX += entry.x
            sumY += entry.y
            sumXY += entry.x * entry.y
            sumX2 += entry.x * entry.x
        }

        val pendiente = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val ultimoValor = entries.last().y
        val prediccion = ultimoValor + pendiente

        return prediccion.coerceIn(0f, 50f)
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Temperatura")
        dataSet.color = Color.rgb(255, 87, 34)
        dataSet.setCircleColor(Color.rgb(255, 87, 34))
        dataSet.lineWidth = 2.5f
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.valueTextSize = 9f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.rgb(255, 87, 34)
        dataSet.fillAlpha = 20
        dataSet.setDrawValues(false)
        dataSet.setDrawHorizontalHighlightIndicator(false)
        dataSet.setDrawHighlightIndicators(true)

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()}°C"
            }
        }

        val lineData = LineData(dataSet)
        lineData.setValueTextColor(Color.DKGRAY)
        lineData.setValueTextSize(9f)
        chartTemperatura.data = lineData

        val xAxis = chartTemperatura.xAxis
        val timeFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        xAxis.valueFormatter = object : IndexAxisValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < timestamps.size) {
                    try {
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timestamps[index])
                        date?.let { timeFormatter.format(it) } ?: timestamps[index]
                    } catch (e: Exception) {
                        timestamps[index]
                    }
                } else {
                    ""
                }
            }
        }

        chartTemperatura.invalidate()
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxTemperaturaValue.text = String.format("%.1f°C", max)
        textViewMinTemperaturaValue.text = String.format("%.1f°C", min)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar listeners
        if (::currentPlantListener.isInitialized) {
            userRef.child("currentPlant").removeEventListener(currentPlantListener)
        }
        if (::sensorDataListener.isInitialized && currentPlantId.isNotEmpty()) {
            sensorDataRef.child(currentPlantId).child("history").removeEventListener(sensorDataListener)
        }
    }
}