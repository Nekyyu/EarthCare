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

class HumedadActivity : AppCompatActivity() {

    private lateinit var chartHumedadExterior: LineChart
    private lateinit var textViewMaxHumedadExteriorValue: TextView
    private lateinit var textViewMinHumedadExteriorValue: TextView
    private lateinit var textViewPrediccionHumedadExteriorValue: TextView
    private lateinit var textViewUltimoDatoHumedadConFecha: TextView
    private lateinit var imageButtonBackHumedad: ImageButton

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference
    private lateinit var sensorDataRef: DatabaseReference
    private var currentPlantId: String = ""
    private var idealHumMin: Float = 40f
    private var idealHumMax: Float = 70f
    private lateinit var currentUserPlants: MutableList<Plant>

    private lateinit var currentPlantListener: ValueEventListener
    private lateinit var sensorDataListener: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_humedad)

        // Inicializar elementos de UI
        chartHumedadExterior = findViewById(R.id.chartHumedadExterior)
        textViewMaxHumedadExteriorValue = findViewById(R.id.textViewMaxHumedadExteriorValue)
        textViewMinHumedadExteriorValue = findViewById(R.id.textViewMinHumedadExteriorValue)
        textViewPrediccionHumedadExteriorValue = findViewById(R.id.textViewPrediccionHumedadExteriorValue)
        textViewUltimoDatoHumedadConFecha = findViewById(R.id.textViewUltimoDatoHumedadConFecha)
        imageButtonBackHumedad = findViewById(R.id.imageButtonBackHumedad)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        val currentUser = auth.currentUser ?: run {
            finish()
            return
        }

        userRef = database.getReference("users").child(currentUser.uid)
        sensorDataRef = database.getReference("sensorData").child(currentUser.uid)
        currentUserPlants = mutableListOf()

        // Configurar gráfica
        configureChart(chartHumedadExterior)

        // Configurar listeners
        setupFirebaseListeners()
        loadUserPlants()

        imageButtonBackHumedad.setOnClickListener {
            onBackPressed()
        }
    }

    private fun loadUserPlants() {
        userRef.child("plants").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentUserPlants.clear()
                for (plantSnapshot in snapshot.children) {
                    val plant = plantSnapshot.getValue(Plant::class.java)
                    plant?.let {
                        it.id = plantSnapshot.key ?: ""
                        currentUserPlants.add(it)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HumedadActivity, "Error al cargar plantas", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupFirebaseListeners() {
        // Listener para cambios en la planta actual
        currentPlantListener = userRef.child("currentPlant").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newPlantId = snapshot.getValue(String::class.java) ?: ""
                if (newPlantId != currentPlantId) {
                    currentPlantId = newPlantId
                    if (currentPlantId.isNotEmpty()) {
                        getIdealHumidity()
                        readFirebaseData()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HumedadActivity, "Error al obtener planta actual", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getIdealHumidity() {
        if (currentPlantId.isEmpty()) return

        userRef.child("plants").child(currentPlantId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Obtener valores con manejo robusto de diferentes tipos y valores por defecto
                idealHumMin = when (val hum = snapshot.child("idealHumidityMin").value) {
                    is Long -> hum.toFloat()
                    is Double -> hum.toFloat()
                    is Int -> hum.toFloat()
                    is Float -> hum
                    else -> 40f // Valor por defecto si no se encuentra o no es válido
                }

                idealHumMax = when (val hum = snapshot.child("idealHumidityMax").value) {
                    is Long -> hum.toFloat()
                    is Double -> hum.toFloat()
                    is Int -> hum.toFloat()
                    is Float -> hum
                    else -> 70f // Valor por defecto si no se encuentra o no es válido
                }

                // Asegurarse de que el mínimo no sea mayor que el máximo
                if (idealHumMin > idealHumMax) {
                    val temp = idealHumMin
                    idealHumMin = idealHumMax
                    idealHumMax = temp
                }

                // Asegurarse de que los valores estén dentro de límites razonables
                idealHumMin = max(0f, idealHumMin)
                idealHumMax = min(100f, idealHumMax)

                updateIdealHumidityLines()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HumedadActivity, "Error al obtener humedades ideales", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun configureChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(Color.TRANSPARENT)

        // Configurar eje X
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.textColor = Color.parseColor("#000000")
        xAxis.textSize = 10f
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.labelRotationAngle = -45f
        xAxis.granularity = 1f
        xAxis.setDrawLabels(false)

        // Configurar eje Y izquierdo
        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.parseColor("#000000")
        leftAxis.textSize = 10f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#CCCCCC")
        leftAxis.gridLineWidth = 0.5f
        leftAxis.setDrawAxisLine(false)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 100f
        leftAxis.setDrawZeroLine(false)

        // Configurar eje Y derecho
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        // Configurar leyenda
        chart.legend.textColor = Color.parseColor("#000000")
        chart.legend.textSize = 12f
        chart.legend.isEnabled = true
        chart.legend.formSize = 12f
        chart.legend.formLineWidth = 2f
        chart.legend.form = Legend.LegendForm.LINE

        // Configurar marcador
        chart.setDrawMarkers(true)
        chart.marker = CustomMarkerView(this, R.layout.custom_marker_view, "%", emptyList())

        // Animación
        chart.animateX(1500, Easing.EaseInOutQuart)
    }

    private fun updateIdealHumidityLines() {
        val leftAxis = chartHumedadExterior.axisLeft
        leftAxis.removeAllLimitLines()

        // Modificado para mostrar líneas incluso con valores bajos
        if (idealHumMin >= 0 && idealHumMax > idealHumMin) {
            // Línea de humedad mínima ideal
            val minLimitLine = LimitLine(idealHumMin, "Mín: ${idealHumMin.roundToInt()}%")
            minLimitLine.lineWidth = 2f
            minLimitLine.lineColor = Color.parseColor("#00BCD4")
            minLimitLine.textColor = Color.parseColor("#00BCD4")
            minLimitLine.textSize = 10f
            minLimitLine.enableDashedLine(10f, 10f, 0f)
            minLimitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP

            // Línea de humedad máxima ideal
            val maxLimitLine = LimitLine(idealHumMax, "Máx: ${idealHumMax.roundToInt()}%")
            maxLimitLine.lineWidth = 2f
            maxLimitLine.lineColor = Color.parseColor("#00BCD4")
            maxLimitLine.textColor = Color.parseColor("#00BCD4")
            maxLimitLine.textSize = 10f
            maxLimitLine.enableDashedLine(10f, 10f, 0f)
            maxLimitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM

            leftAxis.addLimitLine(minLimitLine)
            leftAxis.addLimitLine(maxLimitLine)
        }

        chartHumedadExterior.invalidate()
    }

    private fun readFirebaseData() {
        // Remover listener anterior si existe
        if (::sensorDataListener.isInitialized) {
            val currentUser = auth.currentUser
            val currentPlant = currentUserPlants.find { it.id == currentPlantId }
            val isTargetUserAndPlant = currentUser != null &&
                                     currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3" &&
                                     currentPlant?.name?.lowercase() == "vaporub"

            val refToDetach = if (isTargetUserAndPlant) {
                database.getReference("test")
            } else {
                sensorDataRef.child(currentPlantId).child("history")
            }
            refToDetach.removeEventListener(sensorDataListener)
        }

        if (currentPlantId.isEmpty()) return

        val currentUser = auth.currentUser
        val currentPlant = currentUserPlants.find { it.id == currentPlantId }
        val isTargetUserAndPlant = currentUser != null &&
                                 currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3" &&
                                 currentPlant?.name?.lowercase() == "vaporub"

        val dataQuery = if (isTargetUserAndPlant) {
            database.getReference("test").orderByKey().limitToLast(24)
        } else {
            // Generar datos ficticios para otras plantas
            val fakeData = generateFakeHistory(24)
            val fakeDataRef = sensorDataRef.child(currentPlantId).child("history")
            
            // Limpiar datos anteriores
            fakeDataRef.removeValue().addOnCompleteListener {
                // Guardar nuevos datos ficticios
                fakeData.forEach { (timestamp, data) ->
                    fakeDataRef.child(timestamp.toString()).setValue(data)
                }
            }
            
            fakeDataRef.orderByChild("timestamp").limitToLast(24)
        }

        sensorDataListener = dataQuery.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<Entry>()
                val timestamps = mutableListOf<String>()
                var maxHumedad = Float.NEGATIVE_INFINITY
                var minHumedad = Float.POSITIVE_INFINITY
                var lastHum: Float? = null
                var lastTimestampLabel: String = ""

                snapshot.children.forEachIndexed { index, dataSnapshot ->
                    // Para datos en 'test', la estructura es diferente, necesitamos acceder a los valores dentro del snapshot hijo
                    val sensorData = if (isTargetUserAndPlant) {
                        // Si estamos leyendo de 'test', creamos un objeto SensorData manualmente
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
                            Log.e("HumedadActivity", "Error deserializing test data for key ${dataSnapshot.key}: ${e.message}")
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

                        it.humdedad_ext?.let { humedadValue ->
                            // Asegurarse de usar xValue para añadir al gráfico
                            entries.add(Entry(xValue, humedadValue))
                            if (humedadValue > maxHumedad) maxHumedad = humedadValue
                            if (humedadValue < minHumedad) minHumedad = humedadValue
                            lastHum = humedadValue
                            lastTimestampLabel = timestampLabel
                        }
                    }
                }

                if (entries.isNotEmpty()) {
                    updateChart(entries, timestamps)
                    updateMinMax(maxHumedad, minHumedad)
                    // La predicción puede necesitar ajustarse si la escala de tiempo cambia significativamente
                    // Por ahora, mantenemos la lógica existente
                    val prediccion = calcularPrediccion(entries)
                    textViewPrediccionHumedadExteriorValue.text = String.format("%.1f%%", prediccion)

                    // Actualizar el TextView del último dato con fecha
                    lastHum?.let { hum ->
                         textViewUltimoDatoHumedadConFecha.text = String.format("Último dato: %.1f%% (%s)", hum, lastTimestampLabel)
                    } ?: run { 
                         textViewUltimoDatoHumedadConFecha.text = "Último dato: -- (fecha)"
                    }

                } else {
                    // Limpiar la gráfica si no hay datos
                    updateChart(mutableListOf(), mutableListOf())
                    updateMinMax(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
                    textViewPrediccionHumedadExteriorValue.text = "N/A"
                    textViewUltimoDatoHumedadConFecha.text = "Último dato: -- (fecha)"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HumedadActivity, "Error al leer datos", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun calcularPrediccion(entries: List<Entry>): Float {
        if (entries.size < 2) return entries.firstOrNull()?.y ?: 50f

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

        return prediccion.coerceIn(0f, 100f)
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<String>) {
        val dataSet = LineDataSet(entries, "Humedad Exterior")
        dataSet.color = Color.rgb(0, 150, 136) // Verde agua
        dataSet.setCircleColor(Color.rgb(0, 150, 136))
        dataSet.lineWidth = 2.5f
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.valueTextSize = 9f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.rgb(0, 150, 136)
        dataSet.fillAlpha = 20
        dataSet.setDrawValues(false)
        dataSet.setDrawHorizontalHighlightIndicator(false)
        dataSet.setDrawHighlightIndicators(true)

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.roundToInt()}%"
            }
        }

        val lineData = LineData(dataSet)
        lineData.setValueTextColor(Color.DKGRAY)
        lineData.setValueTextSize(9f)
        chartHumedadExterior.data = lineData

        // Actualizar el marcador con los timestamps
        chartHumedadExterior.marker = CustomMarkerView(this, R.layout.custom_marker_view, "%", timestamps)

        val xAxis = chartHumedadExterior.xAxis
        xAxis.valueFormatter = null

        chartHumedadExterior.invalidate()
    }

    private fun updateMinMax(max: Float, min: Float) {
        textViewMaxHumedadExteriorValue.text = String.format("%.1f%%", max)
        textViewMinHumedadExteriorValue.text = String.format("%.1f%%", min)
    }

    private fun generateFakeHistory(count: Int): Map<Long, SensorData> {
        val random = Random()
        val now = System.currentTimeMillis()
        val data = mutableMapOf<Long, SensorData>()
        
        for (i in 0 until count) {
            val timestamp = now - (count - i) * 3600000 // Cada hora
            val humedad = random.nextFloat() * 30 + 40 // Entre 40 y 70
            
            data[timestamp] = SensorData(
                Hora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp)),
                humdedad_ext = humedad,
                humedad_suelo = random.nextFloat() * 30 + 40,
                luz = random.nextFloat() * 10000 + 5000,
                porcentaje_humedad_suelo = random.nextFloat() * 30 + 40,
                temperatura_ext = random.nextFloat() * 10 + 20,
                timestamp = timestamp
            )
        }
        
        return data
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar listeners
        if (::currentPlantListener.isInitialized) {
            userRef.child("currentPlant").removeEventListener(currentPlantListener)
        }
        if (::sensorDataListener.isInitialized && currentPlantId.isNotEmpty()) {
            sensorDataRef.child(currentPlantId).child("history").removeEventListener(sensorDataListener)
        }
    }
}