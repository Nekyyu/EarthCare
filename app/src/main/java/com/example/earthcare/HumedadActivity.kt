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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class HumedadActivity : AppCompatActivity() {

    private lateinit var chartHumedadExterior: LineChart
    private lateinit var textViewMaxHumedadExteriorValue: TextView
    private lateinit var textViewMinHumedadExteriorValue: TextView
    private lateinit var textViewPrediccionHumedadExteriorValue: TextView
    private lateinit var imageButtonBackHumedad: ImageButton

    private lateinit var database: FirebaseDatabase
    private lateinit var testRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_humedad)

        // Inicializar elementos de UI para la humedad exterior
        chartHumedadExterior = findViewById(R.id.chartHumedadExterior)
        textViewMaxHumedadExteriorValue = findViewById(R.id.textViewMaxHumedadExteriorValue)
        textViewMinHumedadExteriorValue = findViewById(R.id.textViewMinHumedadExteriorValue)
        textViewPrediccionHumedadExteriorValue = findViewById(R.id.textViewPrediccionHumedadExteriorValue)
        imageButtonBackHumedad = findViewById(R.id.imageButtonBackHumedad)

        // Inicializar Firebase
        database = FirebaseDatabase.getInstance()
        testRef = database.getReference("test")

        // Configurar botón de regreso
        imageButtonBackHumedad.setOnClickListener {
            onBackPressed()
        }

        // Configurar gráfica
        configureChart(chartHumedadExterior, 0f, 100f, "Humedad Exterior", Color.rgb(0, 150, 136))

        // Leer datos de Firebase
        readFirebaseData()
    }

    private fun configureChart(chart: LineChart, minY: Float, maxY: Float, label: String, color: Int) {
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
        leftAxis.axisMinimum = minY
        leftAxis.axisMaximum = maxY

        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        chart.legend.textColor = Color.BLACK
        chart.legend.textSize = 12f
        chart.animateX(1500)
    }

    private fun readFirebaseData() {
        testRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val exteriorEntries = mutableListOf<Entry>()
                val timestamps = mutableListOf<String>()
                var maxHumedadExterior = Float.NEGATIVE_INFINITY
                var minHumedadExterior = Float.POSITIVE_INFINITY

                // Obtener los últimos 24 datos
                val dataList = snapshot.children.toList().takeLast(24)

                dataList.forEachIndexed { index, dataSnapshot ->
                    val sensorData = dataSnapshot.getValue(SensorData::class.java)
                    sensorData?.let {
                        val timestamp = it.Hora ?: ""
                        timestamps.add(timestamp)

                        // Añadir punto para humedad exterior si existe
                        it.humdedad_ext?.let { humedadValue ->
                            exteriorEntries.add(Entry(index.toFloat(), humedadValue))
                            if (humedadValue > maxHumedadExterior) maxHumedadExterior = humedadValue
                            if (humedadValue < minHumedadExterior) minHumedadExterior = humedadValue
                        }
                    }
                }

                if (exteriorEntries.isNotEmpty()) {
                    updateChartData(chartHumedadExterior, exteriorEntries, timestamps, "Humedad Exterior", Color.rgb(0, 150, 136), "%", 100f)
                    updateMinMax(textViewMaxHumedadExteriorValue, textViewMinHumedadExteriorValue, maxHumedadExterior, minHumedadExterior, "%.1f%%")
                    val prediccionExterior = calcularPrediccion(exteriorEntries, 0f, 100f)
                    textViewPrediccionHumedadExteriorValue.text = String.format("%.1f%%", prediccionExterior)
                } else {
                    // Si no hay datos de humedad exterior, limpiar la gráfica y los valores
                    chartHumedadExterior.clear()
                    textViewMaxHumedadExteriorValue.text = "--"
                    textViewMinHumedadExteriorValue.text = "--"
                    textViewPrediccionHumedadExteriorValue.text = "--"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejar error
            }
        })
    }

    private fun calcularPrediccion(entries: List<Entry>, minY: Float, maxY: Float): Float {
        if (entries.size < 2) return entries.lastOrNull()?.y ?: (minY + maxY) / 2f

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

        val pendiente = if (n * sumX2 - sumX * sumX == 0f) 0f else (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val ultimoValor = entries.last().y
        val prediccion = ultimoValor + pendiente

        return prediccion.coerceIn(minY, maxY)
    }

    private fun updateChartData(chart: LineChart, entries: List<Entry>, timestamps: List<String>, label: String, color: Int, valueSuffix: String, maxY: Float) {
        val dataSet = LineDataSet(entries, label)
        dataSet.color = color
        dataSet.setCircleColor(color)
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 5f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 8f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = color
        dataSet.fillAlpha = 30
        dataSet.setDrawValues(true)

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()}${valueSuffix}"
            }
        }

        val lineData = LineData(dataSet)
        chart.data = lineData

        val xAxis = chart.xAxis
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

        val leftAxis = chart.axisLeft
        leftAxis.axisMaximum = maxY

        chart.invalidate()
    }

    private fun updateMinMax(textViewMax: TextView, textViewMin: TextView, max: Float, min: Float, format: String) {
        textViewMax.text = String.format(format, max)
        textViewMin.text = String.format(format, min)
    }
} 