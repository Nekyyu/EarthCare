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

        // Initialize UI elements
        chartTemperatura = findViewById(R.id.chartTemperatura)
        textViewMaxTemperaturaValue = findViewById(R.id.textViewMaxTemperaturaValue)
        textViewMinTemperaturaValue = findViewById(R.id.textViewMinTemperaturaValue)
        textViewPrediccionTemperaturaValue = findViewById(R.id.textViewPrediccionTemperaturaValue)
        imageButtonBackTemperatura = findViewById(R.id.imageButtonBackTemperatura)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()
        testRef = database.getReference("test")

        // Set up back button
        imageButtonBackTemperatura.setOnClickListener {
            onBackPressed()
        }

        // Configure chart
        configureChart(chartTemperatura)

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
                var maxTemperatura = Float.NEGATIVE_INFINITY
                var minTemperatura = Float.POSITIVE_INFINITY

                snapshot.children.forEachIndexed { index, dataSnapshot ->
                    val sensorData = dataSnapshot.getValue(SensorData::class.java)
                    sensorData?.let {
                        val timestamp = it.Hora ?: ""
                        timestamps.add(timestamp)

                        it.temperatura_ext?.let { temperaturaExt ->
                            entries.add(Entry(index.toFloat(), temperaturaExt))
                            if (temperaturaExt > maxTemperatura) maxTemperatura = temperaturaExt
                            if (temperaturaExt < minTemperatura) minTemperatura = temperaturaExt
                        }
                    }
                }

                updateChart(entries, timestamps)
                updateMinMax(maxTemperatura, minTemperatura)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Temperatura")
        dataSet.color = Color.RED
        dataSet.setCircleColor(Color.RED)
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 10f

        val lineData = LineData(dataSet)
        chartTemperatura.data = lineData

        // Set X-axis value formatter
        val xAxis = chartTemperatura.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(timestamps)
        xAxis.granularity = 1f // Show each timestamp

        chartTemperatura.invalidate() // Refresh chart
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxTemperaturaValue.text = String.format("%.2f", max)
        textViewMinTemperaturaValue.text = String.format("%.2f", min)
    }
} 