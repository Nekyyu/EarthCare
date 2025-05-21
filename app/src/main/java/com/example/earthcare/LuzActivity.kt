package com.example.earthcare

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.database.*
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
        chart.setDrawGridBackground(false)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(true)
        xAxis.textColor = Color.WHITE
        xAxis.setLabelCount(4, true) // Show approximately 4 labels

        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.WHITE
        leftAxis.setDrawGridLines(true)
        leftAxis.setDrawAxisLine(true)

        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false // Disable right axis

        chart.legend.textColor = Color.WHITE
        chart.animateX(1500)
    }

    private fun readFirebaseData() {
        testRef.addValueEventListener(object : ValueEventListener {
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

                updateChart(entries, timestamps)
                updateMinMax(maxLuz, minLuz)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Nivel de Luz")
        dataSet.color = Color.YELLOW
        dataSet.setCircleColor(Color.YELLOW)
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 10f

        val lineData = LineData(dataSet)
        chartLuz.data = lineData

        // Set X-axis value formatter
        val xAxis = chartLuz.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(timestamps)
        xAxis.granularity = 1f // Show each timestamp

        chartLuz.invalidate() // Refresh chart
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxLuzValue.text = String.format("%.2f", max)
        textViewMinLuzValue.text = String.format("%.2f", min)
    }
} 