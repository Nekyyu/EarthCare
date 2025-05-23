package com.example.earthcare

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Dialog
import android.content.Intent
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

    private lateinit var currentUserPlants: MutableList<Plant>
    private var currentPlantId: String? = null

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
        findViewById<ImageButton>(R.id.PlantaGPT).setOnClickListener {
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
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // Usuario nuevo - crear estructura inicial
                    createNewUserStructure()
                } else {
                    // Usuario existente - cargar datos
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
            // Mostrar diálogo para agregar primera planta
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

                // Cargar planta actual si existe
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

                // Verificar si la planta guardada existe en la lista
                currentPlantId = if (!savedPlantId.isNullOrEmpty() &&
                    currentUserPlants.any { it.id == savedPlantId }) {
                    savedPlantId
                } else if (currentUserPlants.isNotEmpty()) {
                    // Seleccionar la primera planta si la guardada no existe
                    currentUserPlants[0].id
                } else {
                    null
                }

                // Actualizar en Firebase si es necesario
                if (currentPlantId != savedPlantId && !currentPlantId.isNullOrEmpty()) {
                    userRef.child("currentPlant").setValue(currentPlantId)
                }

                // Actualizar UI
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
                // Actualizar imagen y datos de la planta
                findViewById<ImageView>(R.id.ivPlant).setImageResource(
                    when (plant.imageRes) {
                        "tomato" -> R.drawable.ic_tomato
                        "lettuce" -> R.drawable.ic_lettuce
                        "pepper" -> R.drawable.ic_pepper
                        else -> R.drawable.ic_planta
                    }
                )
                // Actualizar el nombre de la planta
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
            // TODO: Implementar lógica para editar planta
            Toast.makeText(this, "Editar ${plantToEdit.name}", Toast.LENGTH_SHORT).show()
            showEditPlantDialog(plantToEdit)
            dialog.dismiss()
        }, { plantToDelete ->
            // Mostrar diálogo de confirmación antes de eliminar
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

            if (customName.isEmpty() || lightMinText.isEmpty() || lightMaxText.isEmpty() || tempMinText.isEmpty() || tempMaxText.isEmpty() || humidityMinText.isEmpty() || humidityMaxText.isEmpty()) {
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

        // Precargar datos de la planta
        dialogBinding.etPlantName.setText(plant.name)
        dialogBinding.etLightMin.setText(plant.idealLightMin.toString())
        dialogBinding.etLightMax.setText(plant.idealLightMax.toString())
        dialogBinding.etTemperatureMin.setText(plant.idealTempMin.toString())
        dialogBinding.etTemperatureMax.setText(plant.idealTempMax.toString())
        dialogBinding.etHumidityMin.setText(plant.idealHumidityMin.toString())
        dialogBinding.etHumidityMax.setText(plant.idealHumidityMax.toString())

        // Seleccionar el tipo de planta correcto en el Spinner (esto puede ser más complejo si los tipos no coinciden exactamente con imageRes)
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
            val lightMinText = dialogBinding.etLightMin.text.toString()
            val lightMaxText = dialogBinding.etLightMax.text.toString()
            val tempMinText = dialogBinding.etTemperatureMin.text.toString()
            val tempMaxText = dialogBinding.etTemperatureMax.text.toString()
            val humidityMinText = dialogBinding.etHumidityMin.text.toString()
            val humidityMaxText = dialogBinding.etHumidityMax.text.toString()

            if (customName.isEmpty() || lightMinText.isEmpty() || lightMaxText.isEmpty() || tempMinText.isEmpty() || tempMaxText.isEmpty() || humidityMinText.isEmpty() || humidityMaxText.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Crear un mapa con los datos actualizados
            val updatedPlantData = hashMapOf<String, Any>(
                "name" to customName,
                "requirements" to "", // requirements se generará en el adapter ahora
                "imageRes" to when (plantType) {
                    "Vegetal" -> "tomato"
                    "Fruta" -> "pepper"
                    "Hierba" -> "lettuce"
                    else -> "default"
                },
                "idealTempMin" to tempMinText.toInt(),
                "idealTempMax" to tempMaxText.toInt(),
                "idealHumidityMin" to humidityMinText.toInt(),
                "idealHumidityMax" to humidityMaxText.toInt(),
                "idealLightMin" to lightMinText.toInt(),
                "idealLightMax" to lightMaxText.toInt()
            )

            // Actualizar la planta en Firebase
            plant.id?.let { plantId ->
                plantsRef.child(plantId).updateChildren(updatedPlantData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Planta actualizada: $customName", Toast.LENGTH_SHORT).show()
                        // La actualización de la lista local y la UI se maneja con el listener de plantsRef
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

    private fun saveNewPlant(plant: Plant) {
        val newPlantRef = plantsRef.push()
        plant.id = newPlantRef.key

        newPlantRef.setValue(plant)
            .addOnSuccessListener {
                // Si es la primera planta, establecer como actual
                if (currentUserPlants.isEmpty()) {
                    userRef.child("currentPlant").setValue(plant.id)
                    currentPlantId = plant.id
                    updatePlantUI()
                }

                // Crear datos iniciales para la planta
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
            calendar.add(Calendar.HOUR, -24) // Últimas 24 horas

            // Asegurar que los rangos sean válidos
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
        Log.d("SensorData", "readLastSensorData called for plant ID: $currentPlantId")

        // Remover listener anterior si existe
        sensorDataListener?.let { listener ->
            currentPlantId?.let { plantId ->
                val currentUser = auth.currentUser
                val isTargetUserAndPlant = currentUser != null &&
                                         currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3" &&
                                         currentUserPlants.find { it.id == plantId }?.name == "vaporub"

                Log.d("SensorData", "isTargetUserAndPlant: $isTargetUserAndPlant")

                val refToDetach = if (isTargetUserAndPlant) {
                    // Apuntar al nodo 'test' para los datos reales
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
            val isTargetUserAndPlant = currentUser != null &&
                                     currentUser.uid == "u6IpDEHmhgaZpeycKOTgnLSBinJ3" &&
                                     currentUserPlants.find { it.id == plantId }?.name == "vaporub"

            Log.d("SensorData", "isTargetUserAndPlant (after detach check): $isTargetUserAndPlant")

            val dataQuery = if (isTargetUserAndPlant) {
                // Apuntar al nodo 'test' y ordenar por clave (timestamp string)
                database.getReference("test").orderByKey().limitToLast(1)
            } else {
                // Referencia a la historia de la planta dentro del usuario (sin cambios)
                database.getReference("sensorData")
                    .child(currentUser?.uid ?: "")
                    .child(plantId)
                    .child("history")
                    .orderByChild("timestamp") // Asegurarse de ordenar por timestamp numérico si existe
                    .limitToLast(1)
            }

            Log.d("SensorData", "Using database query: ${dataQuery.toString()}")

            sensorDataListener = dataQuery.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("SensorData", "Data received from: ${snapshot.ref.toString()}")
                    Log.d("SensorData", "Snapshot exists: ${snapshot.exists()}")
                    Log.d("SensorData", "Snapshot children count: ${snapshot.childrenCount}")

                    if (snapshot.exists()) {
                        // Para datos en 'test', obtenemos el primer (y único) hijo del snapshot limitToLast(1)
                        val latestDataSnapshot = if (isTargetUserAndPlant) {
                            snapshot.children.firstOrNull()
                        } else {
                            // Para la historia de la planta, ya está ordenado y limitado
                            snapshot.children.firstOrNull()
                        }

                        latestDataSnapshot?.let { dataSnapshot ->
                            Log.d("SensorData", "Processing latest entry key: ${dataSnapshot.key}")
                            Log.d("SensorData", "Raw latest entry data: ${dataSnapshot.value}")
                            
                            // Ahora intentamos deserializar usando SensorData
                            val data = dataSnapshot.getValue(SensorData::class.java)
                            
                            if (data != null) {
                                Log.d("SensorData", "Successfully deserialized data: $data")
                                updateSensorData(data)
                            } else {
                                Log.e("SensorData", "Failed to deserialize latest data for key: ${dataSnapshot.key}")
                                // Puedes añadir aquí un manejo de error en la UI si es necesario
                            }
                        } ?: run {
                             Log.d("SensorData", "Snapshot exists but no children found after query.")
                        }

                    } else {
                         Log.d("SensorData", "Snapshot does not exist.")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SensorData", "Error reading sensor data: ${error.message}")
                }
            })
        }
    }

    private fun deletePlant(plant: Plant) {
        plant.id?.let { plantId ->
            // Eliminar datos del sensor asociados a la planta
            plantsRef.child(plantId).child("sensorData").removeValue()

            // Eliminar la planta de la base de datos
            plantsRef.child(plantId).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "Planta eliminada: ${plant.name}", Toast.LENGTH_SHORT).show()

                    // Si la planta eliminada era la seleccionada actualmente, deseleccionarla
                    if (currentPlantId == plantId) {
                        currentPlantId = null
                        userRef.child("currentPlant").removeValue()
                        updatePlantUI()
                        updateSensorUI(0f, 0f, 0f)
                    }

                    // Actualizar la lista local (esto ya lo maneja el listener de plantsRef)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al eliminar la planta: ${plant.name}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateSensorData(data: SensorData) {
        runOnUiThread {
            // Actualizar los valores de los sensores
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

            // Actualizar la hora si está disponible
            data.Hora?.let { hora ->
                // No se actualiza la interfaz de usuario directamente desde el sensorData
            }
        }
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
                    "tomato" -> R.drawable.ic_tomato
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
        constructor() : this(null, "", "", "", 0, 0, 0, 0, 0, 0) // Constructor vacío para Firebase
    }
}