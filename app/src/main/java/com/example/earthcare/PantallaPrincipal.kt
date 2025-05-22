package com.example.earthcare

import android.app.Dialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
        dialogBinding.rvPlants.adapter = PlantAdapter(currentUserPlants) { selectedPlant ->
            currentPlantId = selectedPlant.id
            userRef.child("currentPlant").setValue(selectedPlant.id)
            updatePlantUI()
            readLastSensorData()
            dialog.dismiss()
        }

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
            val lightText = dialogBinding.etLight.text.toString()
            val tempText = dialogBinding.etTemperature.text.toString()
            val humidityText = dialogBinding.etHumidity.text.toString()

            if (customName.isEmpty() || lightText.isEmpty() || tempText.isEmpty() || humidityText.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newPlant = Plant(
                name = customName,
                requirements = "$tempText°C, $humidityText% humedad",
                imageRes = when (plantType) {
                    "Vegetal" -> "tomato"
                    "Fruta" -> "pepper"
                    "Hierba" -> "lettuce"
                    else -> "default"
                },
                idealTemp = tempText.toInt(),
                idealHumidity = humidityText.toInt(),
                idealLight = lightText.toInt()
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
        val plantDataRef = sensorDataRef.child(plantId)

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
        val historyRef = sensorDataRef.child(plantId).child("history")
        val currentPlant = currentUserPlants.find { it.id == plantId }

        currentPlant?.let { plant ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, -24) // Últimas 24 horas

            for (i in 0..23) {
                calendar.add(Calendar.HOUR, 1)

                val fakeData = hashMapOf<String, Any>(
                    "Hora" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.time),
                    "luz" to (plant.idealLight + (-500..500).random()),
                    "temperatura_ext" to (plant.idealTemp + (-3..3).random()),
                    "humdedad_ext" to (plant.idealHumidity + (-10..10).random()),
                    "timestamp" to calendar.timeInMillis
                )

                historyRef.push().setValue(fakeData)
            }
        }
    }

    private fun readLastSensorData() {
        // Remover listener anterior si existe
        sensorDataListener?.let { listener ->
            currentPlantId?.let { plantId ->
                try {
                    sensorDataRef.child(plantId).child("history").removeEventListener(listener)
                } catch (e: Exception) {
                    Log.e("SensorData", "Error removing listener: ${e.message}")
                }
            }
        }

        currentPlantId?.let { plantId ->
            sensorDataListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        // Ordenar por timestamp y obtener el último registro
                        val sortedData = snapshot.children.sortedByDescending {
                            it.child("timestamp").getValue(Long::class.java) ?: 0L
                        }

                        sortedData.firstOrNull()?.let { lastEntry ->
                            // Manejar diferentes formatos numéricos
                            val luz = when (val luzValue = lastEntry.child("luz").value) {
                                is Long -> luzValue.toFloat()
                                is Double -> luzValue.toFloat()
                                is Int -> luzValue.toFloat()
                                else -> 0f
                            }

                            val temp = when (val tempValue = lastEntry.child("temperatura_ext").value) {
                                is Long -> tempValue.toFloat()
                                is Double -> tempValue.toFloat()
                                is Int -> tempValue.toFloat()
                                else -> 0f
                            }

                            val hum = when (val humValue = lastEntry.child("humdedad_ext").value) {
                                is Long -> humValue.toFloat()
                                is Double -> humValue.toFloat()
                                is Int -> humValue.toFloat()
                                else -> 0f
                            }

                            runOnUiThread {
                                textViewUltimoDatoLuz.text = String.format("%.1f lux", luz)
                                textViewUltimoDatoTemperatura.text = String.format("%.1f°C", temp)
                                textViewUltimoDatoHumedad.text = String.format("%.1f%%", hum)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SensorData", "Error processing data: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this@PantallaPrincipal, "Error al procesar datos", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SensorData", "Error reading history: ${error.message}")
                    runOnUiThread {
                        Toast.makeText(this@PantallaPrincipal, "Error al leer historial", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Leer el historial ordenado por timestamp descendente
            try {
                sensorDataRef.child(plantId).child("history")
                    .orderByChild("timestamp")
                    .limitToLast(1)
                    .addValueEventListener(sensorDataListener!!)
            } catch (e: Exception) {
                Log.e("SensorData", "Error adding listener: ${e.message}")
            }
        } ?: run {
            // Si no hay planta seleccionada
            runOnUiThread {
                textViewUltimoDatoLuz.text = "0 lux"
                textViewUltimoDatoTemperatura.text = "0°C"
                textViewUltimoDatoHumedad.text = "0%"
            }
        }
    }

    inner class PlantAdapter(
        private val plants: List<Plant>,
        private val onItemClick: (Plant) -> Unit
    ) : RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {

        inner class PlantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.ivPlant)
            val name: TextView = view.findViewById(R.id.tvPlantName)
            val requirements: TextView = view.findViewById(R.id.tvPlantRequirements)
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
            holder.requirements.text = plant.requirements

            holder.itemView.setOnClickListener { onItemClick(plant) }
        }

        override fun getItemCount() = plants.size
    }

    data class Plant(
        var id: String? = null,
        val name: String = "",
        val requirements: String = "",
        val imageRes: String = "default",
        val idealTemp: Int = 0,
        val idealHumidity: Int = 0,
        val idealLight: Int = 0
    ) {
        constructor() : this(null, "", "", "", 0, 0, 0) // Constructor vacío para Firebase
    }
}