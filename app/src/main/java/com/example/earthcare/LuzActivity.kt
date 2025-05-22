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

class LuzActivity : AppCompatActivity() {

    private lateinit var chartLuz: LineChart
    private lateinit var textViewMaxLuzValue: TextView
    private lateinit var textViewMinLuzValue: TextView
    private lateinit var textViewPrediccionLuzValue: TextView
    private lateinit var imageButtonBackLuz: ImageButton

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference
    private lateinit var sensorDataRef: DatabaseReference
    private var currentPlantId: String = ""
    private var idealLuzMin: Float = 5000f
    private var idealLuzMax: Float = 15000f

    private lateinit var currentPlantListener: ValueEventListener
    private lateinit var sensorDataListener: ValueEventListener

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
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        val currentUser = auth.currentUser ?: run {
            finish()
            return
        }

        userRef = database.getReference("users").child(currentUser.uid)
        sensorDataRef = database.getReference("sensorData").child(currentUser.uid)

        // Configure chart
        configureChart(chartLuz)

        // Set up listeners
        setupFirebaseListeners()

        imageButtonBackLuz.setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupFirebaseListeners() {
        // Listener for current plant changes
        currentPlantListener = userRef.child("currentPlant").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newPlantId = snapshot.getValue(String::class.java) ?: ""
                if (newPlantId != currentPlantId) {
                    currentPlantId = newPlantId
                    if (currentPlantId.isNotEmpty()) {
                        getIdealLight()
                        readFirebaseData()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LuzActivity, "Error getting current plant", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getIdealLight() {
        if (currentPlantId.isEmpty()) return

        userRef.child("plants").child(currentPlantId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get values with robust type handling
                idealLuzMin = when {
                    snapshot.hasChild("idealLightMin") -> when (val light = snapshot.child("idealLightMin").value) {
                        is Long -> light.toFloat()
                        is Double -> light.toFloat()
                        is Int -> light.toFloat()
                        is Float -> light
                        else -> 5000f
                    }
                    snapshot.hasChild("idealLight") -> {
                        val light = when (val lightValue = snapshot.child("idealLight").value) {
                            is Long -> lightValue.toFloat()
                            is Double -> lightValue.toFloat()
                            is Int -> lightValue.toFloat()
                            is Float -> lightValue
                            else -> 10000f
                        }
                        // Special adjustment for low light values
                        when {
                            light < 2000f -> max(0f, light - 500f) // Smaller range for low values
                            else -> light - 5000f // Normal range for other values
                        }
                    }
                    else -> 5000f
                }

                idealLuzMax = when {
                    snapshot.hasChild("idealLightMax") -> when (val light = snapshot.child("idealLightMax").value) {
                        is Long -> light.toFloat()
                        is Double -> light.toFloat()
                        is Int -> light.toFloat()
                        is Float -> light
                        else -> 15000f
                    }
                    snapshot.hasChild("idealLight") -> {
                        val light = when (val lightValue = snapshot.child("idealLight").value) {
                            is Long -> lightValue.toFloat()
                            is Double -> lightValue.toFloat()
                            is Int -> lightValue.toFloat()
                            is Float -> lightValue
                            else -> 10000f
                        }
                        // Special adjustment for low light values
                        when {
                            light < 2000f -> light + 500f // Smaller range for low values
                            else -> light + 5000f // Normal range for other values
                        }
                    }
                    else -> 15000f
                }

                // Ensure values are within reasonable limits
                idealLuzMin = max(0f, idealLuzMin)
                idealLuzMax = min(20000f, idealLuzMax)

                updateIdealLightLines()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LuzActivity, "Error getting ideal light values", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun configureChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(true)
        chart.setBackgroundColor(Color.WHITE)

        // Configure X axis
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

        // Configure left Y axis
        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.DKGRAY
        leftAxis.textSize = 10f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.gridLineWidth = 0.5f
        leftAxis.setDrawAxisLine(true)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 20000f
        leftAxis.setDrawZeroLine(true)
        leftAxis.zeroLineColor = Color.GRAY
        leftAxis.zeroLineWidth = 1f

        // Disable right Y axis
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        // Configure legend
        chart.legend.textColor = Color.DKGRAY
        chart.legend.textSize = 12f
        chart.legend.isEnabled = true
        chart.legend.formSize = 12f
        chart.legend.formLineWidth = 2f
        chart.legend.form = Legend.LegendForm.LINE

        // Configure marker
        chart.setDrawMarkers(true)
        chart.marker = CustomMarkerView(this, R.layout.custom_marker_view, " lux")

        // Animation
        chart.animateX(1500, Easing.EaseInOutQuart)
    }

    private fun updateIdealLightLines() {
        val leftAxis = chartLuz.axisLeft
        leftAxis.removeAllLimitLines()

        // Show lines even with low values
        if (idealLuzMin >= 0 && idealLuzMax > idealLuzMin) {
            // Minimum ideal light line
            val minLimitLine = LimitLine(idealLuzMin, "Mín: ${idealLuzMin.roundToInt()} lux")
            minLimitLine.lineWidth = 1.5f
            minLimitLine.lineColor = Color.rgb(255, 193, 7) // Amber
            minLimitLine.textColor = Color.rgb(255, 193, 7)
            minLimitLine.textSize = 10f
            minLimitLine.enableDashedLine(10f, 10f, 0f)

            // Maximum ideal light line
            val maxLimitLine = LimitLine(idealLuzMax, "Máx: ${idealLuzMax.roundToInt()} lux")
            maxLimitLine.lineWidth = 1.5f
            maxLimitLine.lineColor = Color.rgb(255, 193, 7)
            maxLimitLine.textColor = Color.rgb(255, 193, 7)
            maxLimitLine.textSize = 10f
            maxLimitLine.enableDashedLine(10f, 10f, 0f)

            leftAxis.addLimitLine(minLimitLine)
            leftAxis.addLimitLine(maxLimitLine)
        }

        chartLuz.invalidate()
    }

    private fun readFirebaseData() {
        // Remove previous listener if exists
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
                    var maxLuz = Float.NEGATIVE_INFINITY
                    var minLuz = Float.POSITIVE_INFINITY

                    snapshot.children.forEachIndexed { index, dataSnapshot ->
                        val sensorData = dataSnapshot.getValue(SensorData::class.java)
                        sensorData?.let {
                            val timestamp = it.Hora ?: ""
                            timestamps.add(timestamp)

                            it.luz?.let { luzValue ->
                                entries.add(Entry(index.toFloat(), luzValue))
                                if (luzValue > maxLuz) maxLuz = luzValue
                                if (luzValue < minLuz) minLuz = luzValue
                            }
                        }
                    }

                    if (entries.isNotEmpty()) {
                        updateChart(entries, timestamps)
                        updateMinMax(maxLuz, minLuz)
                        val prediccion = calcularPrediccion(entries)
                        textViewPrediccionLuzValue.text = String.format("%.1f lux", prediccion)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@LuzActivity, "Error reading data", Toast.LENGTH_SHORT).show()
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

        return prediccion.coerceIn(0f, 20000f)
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Luz (lux)")
        dataSet.color = Color.rgb(255, 193, 7) // Amber
        dataSet.setCircleColor(Color.rgb(255, 193, 7))
        dataSet.lineWidth = 2.5f
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.valueTextSize = 9f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.rgb(255, 193, 7)
        dataSet.fillAlpha = 20
        dataSet.setDrawValues(false)
        dataSet.setDrawHorizontalHighlightIndicator(false)
        dataSet.setDrawHighlightIndicators(true)

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()} lux"
            }
        }

        val lineData = LineData(dataSet)
        lineData.setValueTextColor(Color.DKGRAY)
        lineData.setValueTextSize(9f)
        chartLuz.data = lineData

        val xAxis = chartLuz.xAxis
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

        chartLuz.invalidate()
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxLuzValue.text = String.format("%.1f lux", max)
        textViewMinLuzValue.text = String.format("%.1f lux", min)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up listeners
        if (::currentPlantListener.isInitialized) {
            userRef.child("currentPlant").removeEventListener(currentPlantListener)
        }
        if (::sensorDataListener.isInitialized && currentPlantId.isNotEmpty()) {
            sensorDataRef.child(currentPlantId).child("history").removeEventListener(sensorDataListener)
        }
    }
}