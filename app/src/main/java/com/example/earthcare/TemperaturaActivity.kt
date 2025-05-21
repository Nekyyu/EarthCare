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

class TemperaturaActivity : AppCompatActivity() {

    private lateinit var chartTemperatura: LineChart
    private lateinit var textViewMaxTemperaturaValue: TextView
    private lateinit var textViewMinTemperaturaValue: TextView
    private lateinit var textViewPrediccionTemperaturaValue: TextView
    private lateinit var imageButtonBackTemperatura: ImageButton

    private lateinit var database: FirebaseDatabase
    private lateinit var testRef: DatabaseReference

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
        database = FirebaseDatabase.getInstance()
        testRef = database.getReference("test")

        // Configurar botón de regreso
        imageButtonBackTemperatura.setOnClickListener {
            onBackPressed()
        }

        // Configurar gráfica
        configureChart(chartTemperatura)

        // Leer datos de Firebase
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
        leftAxis.axisMaximum = 50f // Temperatura máxima de 50°C

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
                var maxTemperatura = Float.NEGATIVE_INFINITY
                var minTemperatura = Float.POSITIVE_INFINITY
                var sumTemperatura = 0f
                var count = 0

                // Obtener los últimos 24 datos
                val dataList = snapshot.children.toList().takeLast(24)

                dataList.forEachIndexed { index, dataSnapshot ->
                    val sensorData = dataSnapshot.getValue(SensorData::class.java)
                    sensorData?.let {
                        val timestamp = it.Hora ?: ""
                        timestamps.add(timestamp)

                        it.temperatura_ext?.let { tempValue ->
                            entries.add(Entry(index.toFloat(), tempValue))
                            if (tempValue > maxTemperatura) maxTemperatura = tempValue
                            if (tempValue < minTemperatura) minTemperatura = tempValue
                            sumTemperatura += tempValue
                            count++
                        }
                    }
                }

                if (entries.isNotEmpty()) {
                    updateChart(entries, timestamps)
                    updateMinMax(maxTemperatura, minTemperatura)
                    
                    // Calcular predicción basada en la tendencia
                    val prediccion = calcularPrediccion(entries)
                    textViewPrediccionTemperaturaValue.text = String.format("%.1f°C", prediccion)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejar error
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
        return prediccion.coerceIn(0f, 50f)
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Temperatura")
        dataSet.color = Color.rgb(255, 87, 34) // Color naranja
        dataSet.setCircleColor(Color.rgb(255, 87, 34))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 5f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 8f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.rgb(255, 87, 34)
        dataSet.fillAlpha = 30
        dataSet.setDrawValues(true)

        // Formatear los valores del eje Y para mostrar grados Celsius
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()}°C"
            }
        }

        val lineData = LineData(dataSet)
        chartTemperatura.data = lineData

        val xAxis = chartTemperatura.xAxis
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

        chartTemperatura.invalidate()
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxTemperaturaValue.text = String.format("%.1f°C", max)
        textViewMinTemperaturaValue.text = String.format("%.1f°C", min)
    }
} 