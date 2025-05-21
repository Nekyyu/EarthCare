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
import com.github.mikephil.charting.animation.Easing

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

        // Inicializar elementos de UI
        chartLuz = findViewById(R.id.chartLuz)
        textViewMaxLuzValue = findViewById(R.id.textViewMaxLuzValue)
        textViewMinLuzValue = findViewById(R.id.textViewMinLuzValue)
        textViewPrediccionLuzValue = findViewById(R.id.textViewPrediccionLuzValue)
        imageButtonBackLuz = findViewById(R.id.imageButtonBackLuz)

        // Inicializar Firebase
        database = FirebaseDatabase.getInstance()
        testRef = database.getReference("test")

        // Configurar botón de regreso
        imageButtonBackLuz.setOnClickListener {
            onBackPressed()
        }

        // Configurar gráfica
        configureChart(chartLuz, 0f, 100f, "Luz", Color.rgb(255, 193, 7))

        // Leer datos de Firebase
        readFirebaseData()
    }

    private fun configureChart(chart: LineChart, axisMinimum: Float, axisMaximum: Float, title: String, color: Int) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(true)
        chart.setBackgroundColor(Color.WHITE)

        // Configurar el eje X
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

        // Configurar el eje Y izquierdo
        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.DKGRAY
        leftAxis.textSize = 10f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.gridLineWidth = 0.5f
        leftAxis.setDrawAxisLine(true)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 30000f
        leftAxis.setDrawZeroLine(true)
        leftAxis.zeroLineColor = Color.GRAY
        leftAxis.zeroLineWidth = 1f

        // Añadir líneas de límite para el rango óptimo
        val limitHigh = com.github.mikephil.charting.components.LimitLine(20000f, "Ideal")
        limitHigh.lineWidth = 1.5f
        limitHigh.lineColor = Color.rgb(76, 175, 80) // Verde más suave
        limitHigh.textColor = Color.rgb(76, 175, 80)
        limitHigh.textSize = 10f
        limitHigh.enableDashedLine(10f, 10f, 0f)

        val limitLow = com.github.mikephil.charting.components.LimitLine(10000f, "Ideal")
        limitLow.lineWidth = 1.5f
        limitLow.lineColor = Color.rgb(76, 175, 80)
        limitLow.textColor = Color.rgb(76, 175, 80)
        limitLow.textSize = 10f
        limitLow.enableDashedLine(10f, 10f, 0f)

        leftAxis.addLimitLine(limitHigh)
        leftAxis.addLimitLine(limitLow)

        // Deshabilitar el eje Y derecho
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        // Configurar la leyenda
        chart.legend.textColor = Color.DKGRAY
        chart.legend.textSize = 12f
        chart.legend.isEnabled = true
        chart.legend.formSize = 12f
        chart.legend.formLineWidth = 2f
        chart.legend.form = com.github.mikephil.charting.components.Legend.LegendForm.LINE

        // Configurar el marcador
        chart.setDrawMarkers(true)
        chart.marker = CustomMarkerView(this, R.layout.custom_marker_view, " lux")

        // Animación
        chart.animateX(1500, Easing.EaseInOutQuart)
    }

    private fun readFirebaseData() {
        // Calcular la marca de tiempo de inicio (últimas 24 horas)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR, -24)
        val startTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.time)

        // Consultar datos dentro del rango de tiempo
        val query = testRef.orderByChild("Hora").startAt(startTime)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(querySnapshot: DataSnapshot) {
                val entries = mutableListOf<Entry>()
                val timestamps = mutableListOf<String>()
                var maxLuz = Float.NEGATIVE_INFINITY
                var minLuz = Float.POSITIVE_INFINITY

                val dataList = querySnapshot.children.toList()

                dataList.forEachIndexed { index, dataSnapshot ->
                    val sensorData = dataSnapshot.getValue(SensorData::class.java)
                    sensorData?.let {
                        val timestamp = it.Hora ?: ""
                        timestamps.add(timestamp)

                        // Añadir punto para luz si existe
                        it.luz?.let { luzValue ->
                            entries.add(Entry(index.toFloat(), luzValue))
                            if (luzValue > maxLuz) maxLuz = luzValue
                            if (luzValue < minLuz) minLuz = luzValue
                        }
                    }
                }

                if (entries.isNotEmpty()) {
                    updateChartData(chartLuz, entries, timestamps, "Luz", Color.rgb(255, 193, 7), "%", 100f)
                    updateMinMax(textViewMaxLuzValue, textViewMinLuzValue, maxLuz, minLuz, "%.1f%%")
                    val prediccion = calcularPrediccion(entries, 0f, 100f)
                    textViewPrediccionLuzValue.text = String.format("%.1f%%", prediccion)
                } else {
                    // Si no hay datos de luz, limpiar la gráfica y los valores
                    chartLuz.clear()
                    textViewMaxLuzValue.text = "--"
                    textViewMinLuzValue.text = "--"
                    textViewPrediccionLuzValue.text = "--"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejar error
            }
        })
    }

    private fun calcularPrediccion(entries: List<Entry>, axisMinimum: Float, axisMaximum: Float): Float {
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
        return prediccion.coerceIn(axisMinimum, axisMaximum)
    }

    private fun updateChartData(chart: LineChart, entries: List<Entry>, timestamps: List<String>, title: String, color: Int, unit: String, axisMaximum: Float) {
        val dataSet = LineDataSet(entries, title)
        dataSet.color = color
        dataSet.setCircleColor(color)
        dataSet.lineWidth = 2.5f
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.valueTextSize = 9f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = color
        dataSet.fillAlpha = 20
        dataSet.setDrawValues(false) // Ocultar valores por defecto
        dataSet.setDrawHorizontalHighlightIndicator(false)
        dataSet.setDrawHighlightIndicators(true)

        // Formatear los valores
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()} $unit"
            }
        }

        val lineData = LineData(dataSet)
        lineData.setValueTextColor(Color.DKGRAY)
        lineData.setValueTextSize(9f)
        chart.data = lineData

        // Configurar el formato del eje X
        val xAxis = chart.xAxis
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

        // Actualizar la gráfica
        chart.invalidate()
    }

    private fun updateMinMax(textViewMax: TextView, textViewMin: TextView, max: Float, min: Float, format: String) {
        textViewMax.text = String.format(format, max)
        textViewMin.text = String.format(format, min)
    }
}