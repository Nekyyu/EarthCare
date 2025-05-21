package com.example.earthcare

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.*
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.*

class LuzActivity : AppCompatActivity() {

    private lateinit var chartLuz: LineChart
    private lateinit var textViewMaxLuzValue: TextView
    private lateinit var textViewMinLuzValue: TextView
    private lateinit var textViewPrediccionLuzValue: TextView
    private lateinit var imageButtonBackLuz: ImageButton

    private lateinit var database: FirebaseDatabase
    private lateinit var testRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_luz)

        // Initialize UI elements
        chartLuz = findViewById(R.id.chartLuz)
        textViewMaxLuzValue = findViewById(R.id.textViewMaxLuzValue)
        textViewMinLuzValue = findViewById(R.id.textViewMinLuzValue)
        textViewPrediccionLuzValue = findViewById(R.id.textViewPrediccionLuzValue)
        imageButtonBackLuz = findViewById(R.id.imageButtonBackLuz)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()
        testRef = database.getReference("test")

        // Set up back button
        imageButtonBackLuz.setOnClickListener {
            onBackPressed()
        }

        // Configure chart
        configureChart(chartLuz)

        // Read data from Firebase
        readFirebaseData()
    }

    private fun configureChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(true)
        chart.setBackgroundColor(Color.WHITE)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.LTGRAY
        xAxis.gridLineWidth = 0.5f
        xAxis.setDrawAxisLine(true)
        xAxis.textColor = Color.BLACK
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.labelRotationAngle = -45f

        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.BLACK
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.gridLineWidth = 0.5f
        leftAxis.setDrawAxisLine(true)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 1000f // Valor máximo de luz

        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        chart.legend.textColor = Color.BLACK
        chart.legend.textSize = 12f
        chart.animateX(1500)
    }

    private fun readFirebaseData() {
        testRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<Entry>()
                val timestamps = mutableListOf<String>()
                var maxLuz = Float.NEGATIVE_INFINITY
                var minLuz = Float.POSITIVE_INFINITY
                var sumLuz = 0f
                var count = 0

                // Obtener los últimos 24 datos
                val dataList = snapshot.children.toList().takeLast(24)

                dataList.forEachIndexed { index, dataSnapshot ->
                    val sensorData = dataSnapshot.getValue(SensorData::class.java)
                    sensorData?.let {
                        val timestamp = it.Hora ?: ""
                        timestamps.add(timestamp)

                        it.luz?.let { luzValue ->
                            entries.add(Entry(index.toFloat(), luzValue))
                            if (luzValue > maxLuz) maxLuz = luzValue
                            if (luzValue < minLuz) minLuz = luzValue
                            sumLuz += luzValue
                            count++
                        }
                    }
                }

                if (entries.isNotEmpty()) {
                    updateChart(entries, timestamps)
                    updateMinMax(maxLuz, minLuz)
                    
                    // Calcular predicción basada en la tendencia
                    val prediccion = calcularPrediccion(entries)
                    textViewPrediccionLuzValue.text = String.format("%.1f lux", prediccion)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun calcularPrediccion(entries: List<Entry>): Float {
        if (entries.size < 2) return entries.firstOrNull()?.y ?: 0f

        // Calcular la pendiente de la línea de tendencia
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

        // Asegurar que la predicción esté dentro de límites razonables
        return prediccion.coerceIn(0f, 1000f)
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Luz")
        dataSet.color = Color.rgb(255, 193, 7) // Color amarillo
        dataSet.setCircleColor(Color.rgb(255, 193, 7))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 5f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 8f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.rgb(255, 193, 7)
        dataSet.fillAlpha = 30
        dataSet.setDrawValues(true)

        // Formatear los valores del eje Y para mostrar lux
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()} lux"
            }
        }

        val lineData = LineData(dataSet)
        chartLuz.data = lineData

        val xAxis = chartLuz.xAxis
        val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
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
        xAxis.granularity = 1f
        xAxis.setLabelCount(0, false)
        xAxis.labelRotationAngle = -45f

        chartLuz.invalidate()
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxLuzValue.text = String.format("%.1f lux", max)
        textViewMinLuzValue.text = String.format("%.1f lux", min)
    }
} 