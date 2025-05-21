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

class HumedadActivity : AppCompatActivity() {

    private lateinit var chartHumedad: LineChart
    private lateinit var textViewMaxHumedadValue: TextView
    private lateinit var textViewMinHumedadValue: TextView
    private lateinit var textViewPrediccionHumedadValue: TextView
    private lateinit var imageButtonBackHumedad: ImageButton

    private lateinit var database: FirebaseDatabase
    private lateinit var testRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_humedad)

        // Initialize UI elements
        chartHumedad = findViewById(R.id.chartHumedad)
        textViewMaxHumedadValue = findViewById(R.id.textViewMaxHumedadValue)
        textViewMinHumedadValue = findViewById(R.id.textViewMinHumedadValue)
        textViewPrediccionHumedadValue = findViewById(R.id.textViewPrediccionHumedadValue)
        imageButtonBackHumedad = findViewById(R.id.imageButtonBackHumedad)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()
        testRef = database.getReference("test")

        // Set up back button
        imageButtonBackHumedad.setOnClickListener {
            onBackPressed()
        }

        // Configure chart
        configureChart(chartHumedad)

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

                updateChart(entries, timestamps)
                updateMinMax(maxHumedad, minHumedad)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Humedad")
        dataSet.color = Color.BLUE
        dataSet.setCircleColor(Color.BLUE)
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 10f

        val lineData = LineData(dataSet)
        chartHumedad.data = lineData

        // Set X-axis value formatter
        val xAxis = chartHumedad.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(timestamps)
        xAxis.granularity = 1f // Show each timestamp

        chartHumedad.invalidate() // Refresh chart
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxHumedadValue.text = String.format("%.2f", max)
        textViewMinHumedadValue.text = String.format("%.2f", min)
    }
} 