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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.*

class HumedadActivity : AppCompatActivity() {

    private lateinit var chartHumedadExterior: LineChart
    private lateinit var textViewMaxHumedadExteriorValue: TextView
    private lateinit var textViewMinHumedadExteriorValue: TextView
    private lateinit var textViewPrediccionHumedadExteriorValue: TextView
    private lateinit var imageButtonBackHumedad: ImageButton

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference
    private lateinit var sensorDataRef: DatabaseReference
    private var currentPlantId: String = ""
    private var idealHumMin: Float = 40f
    private var idealHumMax: Float = 70f

    private lateinit var currentPlantListener: ValueEventListener
    private lateinit var sensorDataListener: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_humedad)

        // Inicializar elementos de UI
        chartHumedadExterior = findViewById(R.id.chartHumedadExterior)
        textViewMaxHumedadExteriorValue = findViewById(R.id.textViewMaxHumedadExteriorValue)
        textViewMinHumedadExteriorValue = findViewById(R.id.textViewMinHumedadExteriorValue)
        textViewPrediccionHumedadExteriorValue = findViewById(R.id.textViewPrediccionHumedadExteriorValue)
        imageButtonBackHumedad = findViewById(R.id.imageButtonBackHumedad)

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
        configureChart(chartHumedadExterior)

        // Configurar listeners
        setupFirebaseListeners()

        imageButtonBackHumedad.setOnClickListener {
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
                        getIdealHumidity()
                        readFirebaseData()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HumedadActivity, "Error al obtener planta actual", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getIdealHumidity() {
        if (currentPlantId.isEmpty()) return

        userRef.child("plants").child(currentPlantId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Obtener valores con manejo robusto de diferentes tipos y valores por defecto
                idealHumMin = when (val hum = snapshot.child("idealHumidityMin").value) {
                    is Long -> hum.toFloat()
                    is Double -> hum.toFloat()
                    is Int -> hum.toFloat()
                    is Float -> hum
                    else -> 40f // Valor por defecto si no se encuentra o no es válido
                }

                idealHumMax = when (val hum = snapshot.child("idealHumidityMax").value) {
                    is Long -> hum.toFloat()
                    is Double -> hum.toFloat()
                    is Int -> hum.toFloat()
                    is Float -> hum
                    else -> 70f // Valor por defecto si no se encuentra o no es válido
                }

                // Asegurarse de que el mínimo no sea mayor que el máximo
                if (idealHumMin > idealHumMax) {
                    val temp = idealHumMin
                    idealHumMin = idealHumMax
                    idealHumMax = temp
                }

                // Asegurarse de que los valores estén dentro de límites razonables
                idealHumMin = max(0f, idealHumMin)
                idealHumMax = min(100f, idealHumMax)

                updateIdealHumidityLines()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HumedadActivity, "Error al obtener humedades ideales", Toast.LENGTH_SHORT).show()
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
        leftAxis.axisMaximum = 100f
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
        chart.marker = CustomMarkerView(this, R.layout.custom_marker_view, "%")

        // Animación
        chart.animateX(1500, Easing.EaseInOutQuart)
    }

    private fun updateIdealHumidityLines() {
        val leftAxis = chartHumedadExterior.axisLeft
        leftAxis.removeAllLimitLines()

        // Modificado para mostrar líneas incluso con valores bajos
        if (idealHumMin >= 0 && idealHumMax > idealHumMin) {
            // Línea de humedad mínima ideal
            val minLimitLine = LimitLine(idealHumMin, "Mín: ${idealHumMin.roundToInt()}%")
            minLimitLine.lineWidth = 1.5f
            minLimitLine.lineColor = Color.rgb(33, 150, 243) // Azul
            minLimitLine.textColor = Color.rgb(33, 150, 243)
            minLimitLine.textSize = 10f
            minLimitLine.enableDashedLine(10f, 10f, 0f)

            // Línea de humedad máxima ideal
            val maxLimitLine = LimitLine(idealHumMax, "Máx: ${idealHumMax.roundToInt()}%")
            maxLimitLine.lineWidth = 1.5f
            maxLimitLine.lineColor = Color.rgb(33, 150, 243)
            maxLimitLine.textColor = Color.rgb(33, 150, 243)
            maxLimitLine.textSize = 10f
            maxLimitLine.enableDashedLine(10f, 10f, 0f)

            leftAxis.addLimitLine(minLimitLine)
            leftAxis.addLimitLine(maxLimitLine)
        }

        chartHumedadExterior.invalidate()
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
                    var maxHumedad = Float.NEGATIVE_INFINITY
                    var minHumedad = Float.POSITIVE_INFINITY

                    snapshot.children.forEachIndexed { index, dataSnapshot ->
                        val sensorData = dataSnapshot.getValue(SensorData::class.java)
                        sensorData?.let {
                            val timestamp = it.Hora ?: ""
                            timestamps.add(timestamp)

                            it.humdedad_ext?.let { humedadValue ->
                                entries.add(Entry(index.toFloat(), humedadValue))
                                if (humedadValue > maxHumedad) maxHumedad = humedadValue
                                if (humedadValue < minHumedad) minHumedad = humedadValue
                            }
                        }
                    }

                    if (entries.isNotEmpty()) {
                        updateChart(entries, timestamps)
                        updateMinMax(maxHumedad, minHumedad)
                        val prediccion = calcularPrediccion(entries)
                        textViewPrediccionHumedadExteriorValue.text = String.format("%.1f%%", prediccion)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@HumedadActivity, "Error al leer datos", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun calcularPrediccion(entries: List<Entry>): Float {
        if (entries.size < 2) return entries.firstOrNull()?.y ?: 50f

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

        return prediccion.coerceIn(0f, 100f)
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Humedad Exterior")
        dataSet.color = Color.rgb(0, 150, 136) // Verde agua
        dataSet.setCircleColor(Color.rgb(0, 150, 136))
        dataSet.lineWidth = 2.5f
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.valueTextSize = 9f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.rgb(0, 150, 136)
        dataSet.fillAlpha = 20
        dataSet.setDrawValues(false)
        dataSet.setDrawHorizontalHighlightIndicator(false)
        dataSet.setDrawHighlightIndicators(true)

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()}%"
            }
        }

        val lineData = LineData(dataSet)
        lineData.setValueTextColor(Color.DKGRAY)
        lineData.setValueTextSize(9f)
        chartHumedadExterior.data = lineData

        val xAxis = chartHumedadExterior.xAxis
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

        chartHumedadExterior.invalidate()
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxHumedadExteriorValue.text = String.format("%.1f%%", max)
        textViewMinHumedadExteriorValue.text = String.format("%.1f%%", min)
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