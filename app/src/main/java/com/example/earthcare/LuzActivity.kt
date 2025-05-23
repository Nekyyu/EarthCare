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
import android.util.Log

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
                idealLuzMin = when (val light = snapshot.child("idealLightMin").value) {
                    is Long -> light.toFloat()
                    is Double -> light.toFloat()
                    is Int -> light.toFloat()
                    is Float -> light
                    else -> 0f // Valor por defecto si no se encuentra o no es válido
                }

                idealLuzMax = when (val light = snapshot.child("idealLightMax").value) {
                    is Long -> light.toFloat()
                    is Double -> light.toFloat()
                    is Int -> light.toFloat()
                    is Float -> light
                    else -> 20000f // Valor por defecto si no se encuentra o no es válido
                }

                // Ensure min is not greater than max
                if (idealLuzMin > idealLuzMax) {
                    val temp = idealLuzMin
                    idealLuzMin = idealLuzMax
                    idealLuzMax = temp
                }

                // Ensure values are within reasonable limits (optional, depending on desired range)
                idealLuzMin = max(0f, idealLuzMin)
                idealLuzMax = min(100000f, idealLuzMax) // Ajusta el límite superior si es necesario

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
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(Color.TRANSPARENT)

        // Configure X axis
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.textColor = Color.parseColor("#FFFFFF")
        xAxis.textSize = 10f
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.labelRotationAngle = -45f
        xAxis.granularity = 1f
        xAxis.setDrawLabels(false)

        // Configure left Y axis
        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.parseColor("#FFFFFF")
        leftAxis.textSize = 10f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#CCCCCC")
        leftAxis.gridLineWidth = 0.5f
        leftAxis.setDrawAxisLine(false)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 20000f
        leftAxis.setDrawZeroLine(false)

        // Disable right Y axis
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        // Configure legend
        chart.legend.textColor = Color.parseColor("#FFFFFF")
        chart.legend.textSize = 12f
        chart.legend.isEnabled = true
        chart.legend.formSize = 12f
        chart.legend.formLineWidth = 2f
        chart.legend.form = Legend.LegendForm.LINE

        // Configure marker
        chart.setDrawMarkers(true)
        chart.marker = CustomMarkerView(this, R.layout.custom_marker_view, " lux", emptyList())

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
            minLimitLine.lineWidth = 2f
            minLimitLine.lineColor = Color.parseColor("#FFD700")
            minLimitLine.textColor = Color.parseColor("#FFD700")
            minLimitLine.textSize = 10f
            minLimitLine.enableDashedLine(10f, 10f, 0f)
            minLimitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP

            // Maximum ideal light line
            val maxLimitLine = LimitLine(idealLuzMax, "Máx: ${idealLuzMax.roundToInt()} lux")
            maxLimitLine.lineWidth = 2f
            maxLimitLine.lineColor = Color.parseColor("#FFD700")
            maxLimitLine.textColor = Color.parseColor("#FFD700")
            maxLimitLine.textSize = 10f
            maxLimitLine.enableDashedLine(10f, 10f, 0f)
            maxLimitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM

            leftAxis.addLimitLine(minLimitLine)
            leftAxis.addLimitLine(maxLimitLine)
        }

        chartLuz.invalidate()
    }

    private fun readFirebaseData() {
        // Remover previous listener if exists
        if (::sensorDataListener.isInitialized) {
            // Determinar la referencia correcta para remover el listener
            val currentUser = auth.currentUser
            val isTargetUserAndPlant = currentUser != null &&
                                       currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3" &&
                                       // Asumiendo que tienes acceso al nombre de la planta aquí o necesitas obtenerlo
                                       // Por ahora, usaremos solo el currentPlantId y el ID de usuario como indicador
                                       // Esto puede necesitar ajuste si el nombre de la planta es crucial para la decisión de lectura
                                       // Para simplificar, nos basaremos en que si es el usuario objetivo y tenemos un currentPlantId, asumimos que es vaporub si el ID coincide con el esperado para vaporub.
                                       // **NOTA IMPORTANTE:** Si el ID de la planta 'vaporub' es dinámico, esta lógica necesitará ajustarse para obtener el nombre de la planta.
                                       // Dado el contexto anterior, asumiré que currentPlantId se refiere a vaporub cuando isTargetUserAndPlant es true.
                                       currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3"

            val refToDetach = if (isTargetUserAndPlant) {
                // Apuntar al nodo 'test' para los datos reales
                database.getReference("test")
            } else {
                 sensorDataRef.child(currentPlantId).child("history")
            }
             refToDetach.removeEventListener(sensorDataListener)
        }

        if (currentPlantId.isEmpty()) return

        val currentUser = auth.currentUser
        val isTargetUserAndPlant = currentUser != null &&
                                   currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3" &&
                                   // Misma nota importante sobre la identificación de la planta 'vaporub'
                                   currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3"

        val dataQuery = if (isTargetUserAndPlant) {
            // Apuntar al nodo 'test' y ordenar por clave (timestamp string) y limitar a 24
            database.getReference("test").orderByKey().limitToLast(24)
        } else {
            // Referencia a la historia de la planta dentro del usuario (sin cambios)
            sensorDataRef.child(currentPlantId).child("history")
                .orderByChild("timestamp")
                .limitToLast(24)
        }

        sensorDataListener = dataQuery.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<Entry>()
                val timestamps = mutableListOf<String>()
                var maxLuz = Float.NEGATIVE_INFINITY
                var minLuz = Float.POSITIVE_INFINITY

                snapshot.children.forEachIndexed { index, dataSnapshot ->
                     // Para datos en 'test', la estructura es diferente, necesitamos acceder a los valores dentro del snapshot hijo
                    val sensorData = if (isTargetUserAndPlant) {
                        // Si estamos leyendo de 'test', creamos un objeto SensorData manualmente
                        // Esto asume que la estructura dentro de cada timestamp key es plana y coincide con los campos de SensorData
                        try {
                            SensorData(
                                Hora = dataSnapshot.child("Hora").getValue(String::class.java),
                                humdedad_ext = dataSnapshot.child("humdedad_ext").getValue(Float::class.java) ?: dataSnapshot.child("humdedad_ext").getValue(Long::class.java)?.toFloat(),
                                humedad_suelo = dataSnapshot.child("humedad_suelo").getValue(Float::class.java) ?: dataSnapshot.child("humedad_suelo").getValue(Long::class.java)?.toFloat(),
                                luz = dataSnapshot.child("luz").getValue(Float::class.java) ?: dataSnapshot.child("luz").getValue(Long::class.java)?.toFloat(),
                                porcentaje_humedad_suelo = dataSnapshot.child("porcentaje_humedad_suelo").getValue(Float::class.java) ?: dataSnapshot.child("porcentaje_humedad_suelo").getValue(Long::class.java)?.toFloat(),
                                temperatura_ext = dataSnapshot.child("temperatura_ext").getValue(Float::class.java) ?: dataSnapshot.child("temperatura_ext").getValue(Long::class.java)?.toFloat(),
                                timestamp = dataSnapshot.child("timestamp").getValue(Long::class.java)
                            )
                        } catch (e: Exception) {
                             Log.e("LuzActivity", "Error deserializing test data for key ${dataSnapshot.key}: ${e.message}")
                             null
                        }
                    } else {
                         // Si estamos leyendo de la historia de la planta, usamos getValue directamente
                        dataSnapshot.getValue(SensorData::class.java)
                    }

                    sensorData?.let {
                        // Usar el key del snapshot como timestamp para ordenar en la gráfica si leemos de test
                        val xValue = if (isTargetUserAndPlant) {
                            // Si leemos de test, usar el índice para el eje X y la clave como etiqueta
                            index.toFloat()
                        } else {
                            // Si leemos de la historia de la planta, usar el timestamp numérico si está disponible
                            it.timestamp?.toFloat() ?: index.toFloat() // Usar timestamp si existe, si no, usar índice
                        }

                        val timestampLabel = if (isTargetUserAndPlant) {
                            // Si leemos de test, usar la clave como etiqueta (formato yyyy-MM-dd_HH-mm)
                             dataSnapshot.key ?: ""
                        } else {
                            // Si leemos de la historia de la planta, usar el campo Hora o formatear el timestamp
                            it.Hora ?: it.timestamp?.let { ts -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts)) } ?: ""
                        }

                        if (timestampLabel.isNotEmpty()) {
                            timestamps.add(timestampLabel)
                        } else {
                            timestamps.add(index.toString()) // Fallback to index if no timestamp label
                        }

                        it.luz?.let { luzValue ->
                            // Asegurarse de usar xValue para añadir al gráfico
                            entries.add(Entry(xValue, luzValue))
                            if (luzValue > maxLuz) maxLuz = luzValue
                            if (luzValue < minLuz) minLuz = luzValue
                        }
                    }
                }

                if (entries.isNotEmpty()) {
                    updateChart(entries, timestamps)
                    updateMinMax(maxLuz, minLuz)
                    // La predicción puede necesitar ajustarse si la escala de tiempo cambia significativamente
                    // Por ahora, mantenemos la lógica existente
                    val prediccion = calcularPrediccion(entries)
                    textViewPrediccionLuzValue.text = String.format("%.1f lux", prediccion)
                } else {
                     // Limpiar la gráfica si no hay datos
                    updateChart(mutableListOf(), mutableListOf())
                     updateMinMax(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
                     textViewPrediccionLuzValue.text = "N/A"
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

        // Actualizar el marcador con los timestamps
        chartLuz.marker = CustomMarkerView(this, R.layout.custom_marker_view, " lux", timestamps)

        val xAxis = chartLuz.xAxis
        xAxis.valueFormatter = null

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