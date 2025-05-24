package com.example.earthcare

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Dialog
import android.content.Intent
import android.media.Image
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.earthcare.databinding.DialogAlertsBinding
import com.example.earthcare.databinding.DialogPlantQuestionnaireBinding
import com.example.earthcare.databinding.DialogSelectPlantBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PantallaPrincipal : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var sensorDataListener: ValueEventListener? = null
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference
    private lateinit var plantsRef: DatabaseReference
    private lateinit var sensorDataRef: DatabaseReference
    private lateinit var notificationBadge: TextView
    private var activeAlerts = mutableListOf<PlantAlert>()
    private lateinit var currentUserPlants: MutableList<Plant>
    private var currentPlantId: String? = null
    private var isFirstLogin = true

    // Views
    private lateinit var imageViewIconLuz: ImageView
    private lateinit var imageViewIconTemperatura: ImageView
    private lateinit var imageViewIconHumedad: ImageView
    private lateinit var textViewUltimoDatoLuz: TextView
    private lateinit var textViewUltimoDatoTemperatura: TextView
    private lateinit var textViewUltimoDatoHumedad: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_principal)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Verificar autenticación
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Inicializar referencias
        userRef = database.getReference("users").child(currentUser.uid)
        plantsRef = userRef.child("plants")
        sensorDataRef = database.getReference("sensorData").child(currentUser.uid)

        // Inicializar lista de plantas
        currentUserPlants = mutableListOf()

        // Configurar vistas
        setupViews()

        // Cargar datos del usuario
        initializeUserData()

        // Configurar animaciones
        setupAnimations()
    }

    private fun setupViews() {
        // Configurar botones e iconos
        findViewById<FloatingActionButton>(R.id.PlantaGPT).setOnClickListener {
            startActivity(Intent(this, PlantaGPT::class.java))
        }

        imageViewIconLuz = findViewById(R.id.imageViewIconLuz)
        imageViewIconTemperatura = findViewById(R.id.imageViewIconTemperatura)
        imageViewIconHumedad = findViewById(R.id.imageViewIconHumedad)
        textViewUltimoDatoLuz = findViewById(R.id.textViewUltimoDatoLuz)
        textViewUltimoDatoTemperatura = findViewById(R.id.textViewUltimoDatoTemperatura)
        textViewUltimoDatoHumedad = findViewById(R.id.textViewUltimoDatoHumedad)

        findViewById<FloatingActionButton>(R.id.fabChangePlant).setOnClickListener {
            showPlantSelectionDialog()
        }

        // Configurar listeners de clic para los iconos
        imageViewIconLuz.setOnClickListener {
            startActivity(Intent(this, LuzActivity::class.java))
        }
        imageViewIconTemperatura.setOnClickListener {
            startActivity(Intent(this, TemperaturaActivity::class.java))
        }
        imageViewIconHumedad.setOnClickListener {
            startActivity(Intent(this, HumedadActivity::class.java))
        }

        notificationBadge = findViewById(R.id.notificationBadge)
        findViewById<FloatingActionButton>(R.id.fabNotifications).setOnClickListener {
            showAlertsDialog()
        }

    }

    private fun setupAnimations() {
        // Animación de pulso para PlantaGPT
        val pulseAnim = ObjectAnimator.ofPropertyValuesHolder(
            findViewById(R.id.PlantaGPT),
            PropertyValuesHolder.ofFloat("scaleX", 1.1f),
            PropertyValuesHolder.ofFloat("scaleY", 1.1f)
        ).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        // Animación de crecimiento para la planta
        findViewById<ImageView>(R.id.ivPlant).apply {
            scaleX = 0.9f
            scaleY = 0.9f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(1500)
                .start()
        }
    }

    private fun updateSensorUI(luz: Float, temp: Float, hum: Float) {
        // Animación para luz
        if (textViewUltimoDatoLuz.text != String.format("%.1f lux", luz)) {
            imageViewIconLuz.animate().alpha(0.5f).setDuration(200).withEndAction {
                imageViewIconLuz.animate().alpha(1f).setDuration(200)
            }.start()
        }

        // Color para temperatura
        val tempColor = when {
            temp < 15 -> ContextCompat.getColor(this, R.color.coolTemp)
            temp > 30 -> ContextCompat.getColor(this, R.color.hotTemp)
            else -> ContextCompat.getColor(this, R.color.normalTemp)
        }
        imageViewIconTemperatura.setColorFilter(tempColor)

        // Animación para humedad
        if (textViewUltimoDatoHumedad.text != String.format("%.1f%%", hum)) {
            imageViewIconHumedad.animate()
                .translationY(-10f)
                .setDuration(300)
                .withEndAction {
                    imageViewIconHumedad.animate()
                        .translationY(0f)
                        .setDuration(300)
                }.start()
        }

        // Actualizar textos
        textViewUltimoDatoLuz.text = String.format("%.1f lux", luz)
        textViewUltimoDatoTemperatura.text = String.format("%.1f°C", temp)
        textViewUltimoDatoHumedad.text = String.format("%.1f%%", hum)
    }

    private fun initializeUserData() {
        isFirstLogin = true
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    createNewUserStructure()
                } else {
                    loadUserPlants()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PantallaPrincipal, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun createNewUserStructure() {
        val userData = hashMapOf<String, Any>(
            "currentPlant" to "",
            "plants" to hashMapOf<String, Any>()
        )

        userRef.setValue(userData).addOnSuccessListener {
            showFirstPlantDialog()
        }.addOnFailureListener {
            Toast.makeText(this, "Error al crear usuario", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFirstPlantDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¡Bienvenido!")
            .setMessage("Para comenzar, agrega tu primera planta")
            .setPositiveButton("Agregar Planta") { dialog, _ ->
                dialog.dismiss()
                showPlantQuestionnaire()
            }
            .setCancelable(false)
            .show()
    }

    private fun loadUserPlants() {
        plantsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentUserPlants.clear()
                for (plantSnapshot in snapshot.children) {
                    val plant = plantSnapshot.getValue(Plant::class.java)
                    plant?.let {
                        it.id = plantSnapshot.key
                        currentUserPlants.add(it)
                    }
                }
                loadCurrentPlant()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PantallaPrincipal, "Error al cargar plantas", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadCurrentPlant() {
        userRef.child("currentPlant").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val savedPlantId = snapshot.getValue(String::class.java)

                currentPlantId = if (!savedPlantId.isNullOrEmpty() &&
                    currentUserPlants.any { it.id == savedPlantId }) {
                    savedPlantId
                } else if (currentUserPlants.isNotEmpty()) {
                    currentUserPlants[0].id
                } else {
                    null
                }

                if (currentPlantId != savedPlantId && !currentPlantId.isNullOrEmpty()) {
                    userRef.child("currentPlant").setValue(currentPlantId)
                }

                updatePlantUI()
                readLastSensorData()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PantallaPrincipal, "Error al cargar planta actual", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updatePlantUI() {
        currentPlantId?.let { plantId ->
            currentUserPlants.find { it.id == plantId }?.let { plant ->
                findViewById<ImageView>(R.id.ivPlant).setImageResource(
                    when (plant.imageRes) {
                        "tomato" -> R.drawable.vaporub
                        "lettuce" -> R.drawable.ic_lettuce
                        "pepper" -> R.drawable.ic_pepper
                        else -> R.drawable.ic_planta
                    }
                )
                findViewById<TextView>(R.id.tvPlantName).text = plant.name
            }
        }
    }

    private fun showPlantSelectionDialog() {

        val dialogBinding = DialogSelectPlantBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setTitle("Seleccionar Planta")
            .setCancelable(true)
            .create()

        dialogBinding.rvPlants.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvPlants.adapter = PlantAdapter(currentUserPlants, { selectedPlant ->
            currentPlantId = selectedPlant.id
            userRef.child("currentPlant").setValue(selectedPlant.id)
            updatePlantUI()
            readLastSensorData()
            dialog.dismiss()
        }, { plantToEdit ->
            showEditPlantDialog(plantToEdit)
            dialog.dismiss()
        }, { plantToDelete ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que quieres eliminar a ${plantToDelete.name}?")
                .setPositiveButton("Eliminar") { dialog, _ ->
                    deletePlant(plantToDelete)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        })

        dialogBinding.btnAddNewPlant.setOnClickListener {
            dialog.dismiss()
            showPlantQuestionnaire()
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPlantQuestionnaire() {
        val dialogBinding = DialogPlantQuestionnaireBinding.inflate(layoutInflater)
        dialogBinding.informationLight.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Recomendación de Luz ( 1 Lux = 1 lumen/m² )")
                .setMessage("Vegetales              20,000 - 50,000\n" +
                            "Frutales               30,000 - 60,000\n" +
                            "Hierbas                15,000 - 40,000\n" +
                            "Flores                 20,000 - 60,000\n" +
                            "Arboles                25,000 - 70,000\n" +
                            "Cactus y Suculentas    30,000 - 100,000")
                .setPositiveButton("OK", null)
                .show()
        }
        dialogBinding.informationTemperature.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Recomendación de Temperatura (Grados Centigrados)")
                .setMessage("Vegetales              18 – 27\n" +
                            "Frutales               20 – 30\n" +
                            "Hierbas                18 – 25\n" +
                            "Flores                 16 – 26\n" +
                            "Árboles                15 – 30\n" +
                            "Cactus y Suculentas    20 – 35")
                .setPositiveButton("OK", null)
                .show()
        }
        dialogBinding.informationHumidity.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Recomendación de Humedad (%)")
                .setMessage("Vegetales              60 – 80\n" +
                        "Frutales               50 – 70\n" +
                        "Hierbas                50 – 70\n" +
                        "Flores                 50 – 70\n" +
                        "Árboles                40 – 70\n" +
                        "Cactus y Suculentas    10 – 40")
                .setPositiveButton("OK", null)
                .show()
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setTitle("Agregar Nueva Planta")
            .setCancelable(false)
            .create()

        val plantTypes = arrayOf("Vegetal", "Fruta", "Hierba", "Flor", "Árbol", "Cactus/Suculenta", "Otro")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, plantTypes)
        dialogBinding.spinnerPlantType.adapter = adapter

        dialogBinding.btnSavePlant.setOnClickListener {
            val plantType = dialogBinding.spinnerPlantType.selectedItem.toString()
            val customName = dialogBinding.etPlantName.text.toString().trim()
            val lightMinText = dialogBinding.etLightMin.text.toString()
            val lightMaxText = dialogBinding.etLightMax.text.toString()
            val tempMinText = dialogBinding.etTemperatureMin.text.toString()
            val tempMaxText = dialogBinding.etTemperatureMax.text.toString()
            val humidityMinText = dialogBinding.etHumidityMin.text.toString()
            val humidityMaxText = dialogBinding.etHumidityMax.text.toString()

            if (customName.isEmpty() || lightMinText.isEmpty() || lightMaxText.isEmpty() ||
                tempMinText.isEmpty() || tempMaxText.isEmpty() || humidityMinText.isEmpty() || humidityMaxText.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newPlant = Plant(
                name = customName,
                imageRes = when (plantType) {
                    "Vegetal" -> "tomato"
                    "Fruta" -> "pepper"
                    "Hierba" -> "lettuce"
                    else -> "default"
                },
                idealTempMin = tempMinText.toInt(),
                idealTempMax = tempMaxText.toInt(),
                idealHumidityMin = humidityMinText.toInt(),
                idealHumidityMax = humidityMaxText.toInt(),
                idealLightMin = lightMinText.toInt(),
                idealLightMax = lightMaxText.toInt()
            )

            saveNewPlant(newPlant)
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
            if (currentUserPlants.isEmpty()) showFirstPlantDialog()
        }

        dialog.show()
    }

    private fun saveNewPlant(plant: Plant) {
        val newPlantRef = plantsRef.push()
        plant.id = newPlantRef.key

        newPlantRef.setValue(plant)
            .addOnSuccessListener {
                if (currentUserPlants.isEmpty()) {
                    userRef.child("currentPlant").setValue(plant.id)
                    currentPlantId = plant.id
                    updatePlantUI()
                }
                initializeSensorDataForPlant(plant.id!!)
                Toast.makeText(this, "Planta agregada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun initializeSensorDataForPlant(plantId: String) {
        val plantDataRef = plantsRef.child(plantId).child("sensorData")
        plantDataRef.child("history").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    generateFakeHistory(plantId)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("SensorData", "Error checking history: ${error.message}")
            }
        })
    }

    private fun generateFakeHistory(plantId: String) {
        val historyRef = plantsRef.child(plantId).child("sensorData").child("history")
        val currentPlant = currentUserPlants.find { it.id == plantId }

        currentPlant?.let { plant ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, -24)

            val lightMin = minOf(plant.idealLightMin, plant.idealLightMax)
            val lightMax = maxOf(plant.idealLightMin, plant.idealLightMax)
            val tempMin = minOf(plant.idealTempMin, plant.idealTempMax)
            val tempMax = maxOf(plant.idealTempMin, plant.idealTempMax)
            val humidityMin = minOf(plant.idealHumidityMin, plant.idealHumidityMax)
            val humidityMax = maxOf(plant.idealHumidityMin, plant.idealHumidityMax)

            for (i in 0..23) {
                calendar.add(Calendar.HOUR, 1)

                val fakeData = hashMapOf<String, Any>(
                    "Hora" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.time),
                    "luz" to (lightMin..lightMax).random(),
                    "temperatura_ext" to (tempMin..tempMax).random(),
                    "humdedad_ext" to (humidityMin..humidityMax).random(),
                    "timestamp" to calendar.timeInMillis
                )

                historyRef.push().setValue(fakeData)
            }
        }
    }

    private fun readLastSensorData() {
        Log.d("SensorData", "Leyendo datos para planta: $currentPlantId")

        sensorDataListener?.let { listener ->
            currentPlantId?.let { plantId ->
                val currentUser = auth.currentUser
                val currentPlant = currentUserPlants.find { it.id == plantId }
                val isTargetUserAndPlant = currentUser != null &&
                        currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3" &&
                        currentPlant?.name?.lowercase() == "vaporub"

                val refToDetach = if (isTargetUserAndPlant) {
                    database.getReference("test")
                } else {
                    database.getReference("sensorData")
                        .child(currentUser?.uid ?: "")
                        .child(plantId)
                        .child("history")
                }
                refToDetach.removeEventListener(listener)
            }
        }

        currentPlantId?.let { plantId ->
            val currentUser = auth.currentUser
            val currentPlant = currentUserPlants.find { it.id == plantId }
            val isTargetUserAndPlant = currentUser != null &&
                    currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3" &&
                    currentPlant?.name?.lowercase() == "vaporub"

            val dataQuery = if (isTargetUserAndPlant) {
                database.getReference("test").orderByKey().limitToLast(1)
            } else {
                database.getReference("sensorData")
                    .child(currentUser?.uid ?: "")
                    .child(plantId)
                    .child("history")
                    .orderByChild("timestamp")
                    .limitToLast(1)
            }

            sensorDataListener = dataQuery.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("SensorData", "Datos recibidos: ${snapshot.value}")

                    if (snapshot.exists()) {
                        val latestDataSnapshot = if (isTargetUserAndPlant) {
                            snapshot.children.firstOrNull()
                        } else {
                            snapshot.children.firstOrNull()
                        }

                        latestDataSnapshot?.let { dataSnapshot ->
                            val data = dataSnapshot.getValue(SensorData::class.java)
                            if (data != null) {
                                Log.d("SensorData", "Datos deserializados: Luz=${data.luz}, Temp=${data.temperatura_ext}, Hum=${data.humdedad_ext}")
                                updateSensorData(data)
                            } else {
                                Log.e("SensorData", "Error al deserializar datos")
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SensorData", "Error reading sensor data: ${error.message}")
                }
            })
        }
    }

    private fun updateSensorData(data: SensorData) {
        runOnUiThread {
            data.luz?.let { luz ->
                textViewUltimoDatoLuz.text = String.format("%.1f lux", luz)
                imageViewIconLuz.alpha = if (luz > 0f) 1f else 0.5f
            }

            data.temperatura_ext?.let { temp ->
                textViewUltimoDatoTemperatura.text = String.format("%.1f°C", temp)
                imageViewIconTemperatura.setColorFilter(
                    when {
                        temp < 15 -> ContextCompat.getColor(this, R.color.coolTemp)
                        temp > 30 -> ContextCompat.getColor(this, R.color.hotTemp)
                        else -> ContextCompat.getColor(this, R.color.normalTemp)
                    }
                )
            }

            data.humdedad_ext?.let { hum ->
                textViewUltimoDatoHumedad.text = String.format("%.1f%%", hum)
                imageViewIconHumedad.alpha = if (hum > 0f) 1f else 0.5f
            }

            checkPlantConditions(data)
        }
    }

    private fun checkPlantConditions(sensorData: SensorData) {
        currentPlantId?.let { plantId ->
            val currentPlant = currentUserPlants.find { it.id == plantId }
            currentPlant?.let { plant ->
                val newAlerts = mutableListOf<PlantAlert>()

                // Verificar luz
                sensorData.luz?.let { luz ->
                    if (luz < plant.idealLightMin) {
                        newAlerts.add(PlantAlert(
                            plant.name,
                            PlantAlert.AlertType.LIGHT,
                            luz,
                            plant.idealLightMin.toFloat(),
                            plant.idealLightMax.toFloat()
                        ))
                        Log.d("Alerta", "Luz baja: $luz < ${plant.idealLightMin}")
                    } else if (luz > plant.idealLightMax) {
                        newAlerts.add(PlantAlert(
                            plant.name,
                            PlantAlert.AlertType.LIGHT,
                            luz,
                            plant.idealLightMin.toFloat(),
                            plant.idealLightMax.toFloat()
                        ))
                        Log.d("Alerta", "Luz alta: $luz > ${plant.idealLightMax}")
                    } else {

                    }
                }

                // Verificar temperatura
                sensorData.temperatura_ext?.let { temp ->
                    if (temp < plant.idealTempMin) {
                        newAlerts.add(PlantAlert(
                            plant.name,
                            PlantAlert.AlertType.TEMPERATURE,
                            temp,
                            plant.idealTempMin.toFloat(),
                            plant.idealTempMax.toFloat()
                        ))
                        Log.d("Alerta", "Temp baja: $temp < ${plant.idealTempMin}")
                    } else if (temp > plant.idealTempMax) {
                        newAlerts.add(PlantAlert(
                            plant.name,
                            PlantAlert.AlertType.TEMPERATURE,
                            temp,
                            plant.idealTempMin.toFloat(),
                            plant.idealTempMax.toFloat()
                        ))
                        Log.d("Alerta", "Temp alta: $temp > ${plant.idealTempMax}")
                    } else {

                    }
                }

                // Verificar humedad
                sensorData.humdedad_ext?.let { hum ->
                    if (hum < plant.idealHumidityMin) {
                        newAlerts.add(PlantAlert(
                            plant.name,
                            PlantAlert.AlertType.HUMIDITY,
                            hum,
                            plant.idealHumidityMin.toFloat(),
                            plant.idealHumidityMax.toFloat()
                        ))
                        Log.d("Alerta", "Humedad baja: $hum < ${plant.idealHumidityMin}")
                    } else if (hum > plant.idealHumidityMax) {
                        newAlerts.add(PlantAlert(
                            plant.name,
                            PlantAlert.AlertType.HUMIDITY,
                            hum,
                            plant.idealHumidityMin.toFloat(),
                            plant.idealHumidityMax.toFloat()
                        ))
                        Log.d("Alerta", "Humedad alta: $hum > ${plant.idealHumidityMax}")
                    } else {

                    }
                }

                // Solo actualizar si hay nuevas alertas diferentes a las anteriores
                if (newAlerts.isNotEmpty() && (isFirstLogin || newAlerts != activeAlerts)) {
                    activeAlerts.clear()
                    activeAlerts.addAll(newAlerts)
                    updateAlertsUI()
                }

                isFirstLogin = false
            }
        }
    }

    private fun updateAlertsUI() {
        runOnUiThread {
            if (activeAlerts.isNotEmpty()) {
                notificationBadge.text = activeAlerts.size.toString()
                notificationBadge.visibility = View.VISIBLE

                // Solo mostrar notificación si es el primer login
                if (isFirstLogin) {
                    showAlertNotification()
                }

                val scaleAnim = ObjectAnimator.ofPropertyValuesHolder(
                    notificationBadge,
                    PropertyValuesHolder.ofFloat("scaleX", 1.2f),
                    PropertyValuesHolder.ofFloat("scaleY", 1.2f)
                ).apply {
                    duration = 500
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
            } else {
                notificationBadge.visibility = View.GONE
            }
        }
    }

    private fun showAlertNotification() {
        Toast.makeText(this, "¡Nueva alerta en tus plantas!", Toast.LENGTH_SHORT).show()
    }

    private fun showAlertsDialog() {
        if (activeAlerts.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Sin alertas")
                .setMessage("Todas tus plantas están en condiciones óptimas")
                .setPositiveButton("OK", null)
                .show()
            return
        }


        val dialogBinding = DialogAlertsBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setTitle("Alertas de tus plantas")
            .setPositiveButton("OK", null)
            .create()

        dialogBinding.rvAlerts.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvAlerts.adapter = AlertAdapter(activeAlerts)

        dialog.show()
    }

    private fun deletePlant(plant: Plant) {
        plant.id?.let { plantId ->
            plantsRef.child(plantId).child("sensorData").removeValue()
            plantsRef.child(plantId).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "Planta eliminada: ${plant.name}", Toast.LENGTH_SHORT).show()
                    if (currentPlantId == plantId) {
                        currentPlantId = null
                        userRef.child("currentPlant").removeValue()
                        updatePlantUI()
                        updateSensorUI(0f, 0f, 0f)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al eliminar la planta: ${plant.name}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showEditPlantDialog(plant: Plant) {
        val dialogBinding = DialogPlantQuestionnaireBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setTitle("Editar Planta: ${plant.name}")
            .setCancelable(false)
            .create()

        val plantTypes = arrayOf("Vegetal", "Fruta", "Hierba", "Flor", "Árbol", "Cactus/Suculenta", "Otro")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, plantTypes)
        dialogBinding.spinnerPlantType.adapter = adapter

        dialogBinding.etPlantName.setText(plant.name)
        dialogBinding.etLightMin.setText(plant.idealLightMin.toString())
        dialogBinding.etLightMax.setText(plant.idealLightMax.toString())
        dialogBinding.etTemperatureMin.setText(plant.idealTempMin.toString())
        dialogBinding.etTemperatureMax.setText(plant.idealTempMax.toString())
        dialogBinding.etHumidityMin.setText(plant.idealHumidityMin.toString())
        dialogBinding.etHumidityMax.setText(plant.idealHumidityMax.toString())

        val imageResToPlantType = mapOf(
            "tomato" to "Vegetal",
            "pepper" to "Fruta",
            "lettuce" to "Hierba"
        )
        val plantTypeFromImage = imageResToPlantType[plant.imageRes] ?: "Otro"
        val spinnerPosition = plantTypes.indexOf(plantTypeFromImage)
        if (spinnerPosition >= 0) {
            dialogBinding.spinnerPlantType.setSelection(spinnerPosition)
        }

        dialogBinding.btnSavePlant.setOnClickListener {
            val plantType = dialogBinding.spinnerPlantType.selectedItem.toString()
            val customName = dialogBinding.etPlantName.text.toString().trim()

            val lightMin = dialogBinding.etLightMin.text.toString().takeIf { it.isNotEmpty() }?.toInt() ?: plant.idealLightMin
            val lightMax = dialogBinding.etLightMax.text.toString().takeIf { it.isNotEmpty() }?.toInt() ?: plant.idealLightMax
            val tempMin = dialogBinding.etTemperatureMin.text.toString().takeIf { it.isNotEmpty() }?.toInt() ?: plant.idealTempMin
            val tempMax = dialogBinding.etTemperatureMax.text.toString().takeIf { it.isNotEmpty() }?.toInt() ?: plant.idealTempMax
            val humidityMin = dialogBinding.etHumidityMin.text.toString().takeIf { it.isNotEmpty() }?.toInt() ?: plant.idealHumidityMin
            val humidityMax = dialogBinding.etHumidityMax.text.toString().takeIf { it.isNotEmpty() }?.toInt() ?: plant.idealHumidityMax

            if (customName.isEmpty()) {
                Toast.makeText(this, "El nombre de la planta es requerido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedPlantData = hashMapOf<String, Any>(
                "name" to customName,
                "imageRes" to when (plantType) {
                    "Vegetal" -> "tomato"
                    "Fruta" -> "pepper"
                    "Hierba" -> "lettuce"
                    else -> "default"
                },
                "idealTempMin" to tempMin,
                "idealTempMax" to tempMax,
                "idealHumidityMin" to humidityMin,
                "idealHumidityMax" to humidityMax,
                "idealLightMin" to lightMin,
                "idealLightMax" to lightMax
            )

            plant.id?.let { plantId ->
                plantsRef.child(plantId).updateChildren(updatedPlantData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Planta actualizada: $customName", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al actualizar la planta: $customName", Toast.LENGTH_SHORT).show()
                    }
            }

            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    inner class AlertAdapter(private val alerts: List<PlantAlert>) :
        RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

        inner class AlertViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivAlertIcon)
            val title: TextView = view.findViewById(R.id.tvAlertTitle)
            val message: TextView = view.findViewById(R.id.tvAlertMessage)
            val plantName: TextView = view.findViewById(R.id.tvPlantName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_alert, parent, false)
            return AlertViewHolder(view)
        }

        override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
            val alert = alerts[position]

            holder.plantName.text = alert.plantName

            when (alert.alertType) {
                PlantAlert.AlertType.LIGHT -> {
                    holder.icon.setImageResource(R.drawable.ic_alert_light)
                    if (alert.currentValue < alert.idealMin) {
                        holder.title.text = "Poca luz"
                        holder.message.text = "Necesita al menos ${alert.idealMin} lux (actual: ${alert.currentValue} lux)"
                        holder.icon.setColorFilter(ContextCompat.getColor(this@PantallaPrincipal, R.color.yellowAlert))
                    } else {
                        holder.title.text = "Mucha luz"
                        holder.message.text = "Necesita máximo ${alert.idealMax} lux (actual: ${alert.currentValue} lux)"
                        holder.icon.setColorFilter(ContextCompat.getColor(this@PantallaPrincipal, R.color.orangeAlert))
                    }
                }
                PlantAlert.AlertType.TEMPERATURE -> {
                    holder.icon.setImageResource(R.drawable.ic_alert_temp)
                    if (alert.currentValue < alert.idealMin) {
                        holder.title.text = "Temperatura baja"
                        holder.message.text = "Necesita al menos ${alert.idealMin}°C (actual: ${alert.currentValue}°C)"
                        holder.icon.setColorFilter(ContextCompat.getColor(this@PantallaPrincipal, R.color.blueAlert))
                    } else {
                        holder.title.text = "Temperatura alta"
                        holder.message.text = "Necesita máximo ${alert.idealMax}°C (actual: ${alert.currentValue}°C)"
                        holder.icon.setColorFilter(ContextCompat.getColor(this@PantallaPrincipal, R.color.redAlert))
                    }
                }
                PlantAlert.AlertType.HUMIDITY -> {
                    holder.icon.setImageResource(R.drawable.ic_alert_humidity)
                    if (alert.currentValue < alert.idealMin) {
                        holder.title.text = "Humedad baja"
                        holder.message.text = "Necesita al menos ${alert.idealMin}% (actual: ${alert.currentValue}%)"
                        holder.icon.setColorFilter(ContextCompat.getColor(this@PantallaPrincipal, R.color.lightBlue))
                    } else {
                        holder.title.text = "Humedad alta"
                        holder.message.text = "Necesita máximo ${alert.idealMax}% (actual: ${alert.currentValue}%)"
                        holder.icon.setColorFilter(ContextCompat.getColor(this@PantallaPrincipal, R.color.darkBlue))
                    }
                }
            }
        }

        override fun getItemCount() = alerts.size
    }

    inner class PlantAdapter(
        private val plants: List<Plant>,
        private val onItemClick: (Plant) -> Unit,
        private val onEditClick: (Plant) -> Unit,
        private val onDeleteClick: (Plant) -> Unit
    ) : RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {

        inner class PlantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.ivPlant)
            val name: TextView = view.findViewById(R.id.tvPlantName)
            val requirements: TextView = view.findViewById(R.id.tvPlantRequirements)
            val editButton: ImageButton = view.findViewById(R.id.btnEditPlant)
            val deleteButton: ImageButton = view.findViewById(R.id.btnDeletePlant)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_plant, parent, false)
            return PlantViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlantViewHolder, position: Int) {
            val plant = plants[position]
            holder.image.setImageResource(
                when (plant.imageRes) {
                    "tomato" -> R.drawable.vaporub
                    "lettuce" -> R.drawable.ic_lettuce
                    "pepper" -> R.drawable.ic_pepper
                    else -> R.drawable.ic_planta
                }
            )
            holder.name.text = plant.name
            holder.requirements.text = "Temp: ${plant.idealTempMin}-${plant.idealTempMax}°C, Hum: ${plant.idealHumidityMin}-${plant.idealHumidityMax}%, Luz: ${plant.idealLightMin}-${plant.idealLightMax} lux"

            holder.itemView.setOnClickListener { onItemClick(plant) }
            holder.editButton.setOnClickListener { onEditClick(plant) }
            holder.deleteButton.setOnClickListener { onDeleteClick(plant) }
        }

        override fun getItemCount() = plants.size
    }

    data class PlantAlert(
        val plantName: String,
        val alertType: AlertType,
        val currentValue: Float,
        val idealMin: Float,
        val idealMax: Float
    ) {
        enum class AlertType { LIGHT, TEMPERATURE, HUMIDITY }
    }

    data class Plant(
        var id: String? = null,
        val name: String = "",
        val requirements: String = "",
        val imageRes: String = "default",
        val idealTempMin: Int = 0,
        val idealTempMax: Int = 0,
        val idealHumidityMin: Int = 0,
        val idealHumidityMax: Int = 0,
        val idealLightMin: Int = 0,
        val idealLightMax: Int = 0
    ) {
        constructor() : this(null, "", "", "", 0, 0, 0, 0, 0, 0)
    }
}