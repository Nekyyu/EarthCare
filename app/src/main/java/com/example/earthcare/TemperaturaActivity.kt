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
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.animation.Easing

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
        leftAxis.axisMaximum = 40f
        leftAxis.setDrawZeroLine(true)
        leftAxis.zeroLineColor = Color.GRAY
        leftAxis.zeroLineWidth = 1f

        // Añadir líneas de límite para el rango óptimo
        val limitHigh = LimitLine(25f, "Ideal")
        limitHigh.lineWidth = 1.5f
        limitHigh.lineColor = Color.rgb(76, 175, 80) // Verde más suave
        limitHigh.textColor = Color.rgb(76, 175, 80)
        limitHigh.textSize = 10f
        limitHigh.enableDashedLine(10f, 10f, 0f)

        val limitLow = LimitLine(18f, "Ideal")
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
        chart.legend.form = Legend.LegendForm.LINE

        // Configurar el marcador
        chart.setDrawMarkers(true)
        chart.marker = CustomMarkerView(this, R.layout.custom_marker_view, "°C")

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
                var maxTemperatura = Float.NEGATIVE_INFINITY
                var minTemperatura = Float.POSITIVE_INFINITY
                var sumTemperatura = 0f
                var count = 0

                val dataList = querySnapshot.children.toList()

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
                } else {
                    // Limpiar gráfico y valores si no hay datos
                    chartTemperatura.clear()
                    textViewMaxTemperaturaValue.text = "--"
                    textViewMinTemperaturaValue.text = "--"
                    textViewPrediccionTemperaturaValue.text = "--"
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
        dataSet.color = Color.rgb(255, 87, 34) // Naranja
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
        dataSet.setDrawValues(false) // Ocultar valores por defecto
        dataSet.setDrawHorizontalHighlightIndicator(false)
        dataSet.setDrawHighlightIndicators(true)

        // Formatear los valores
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()}°C"
            }
        }

        val lineData = LineData(dataSet)
        lineData.setValueTextColor(Color.DKGRAY)
        lineData.setValueTextSize(9f)
        chartTemperatura.data = lineData

        // Configurar el formato del eje X
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

        // Actualizar la gráfica
        chartTemperatura.invalidate()
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxTemperaturaValue.text = String.format("%.1f°C", max)
        textViewMinTemperaturaValue.text = String.format("%.1f°C", min)
    }
}