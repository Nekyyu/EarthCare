package com.example.earthcare

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.database.*

class PantallaPrincipal : AppCompatActivity() {

    private lateinit var sharedPref: SharedPreferences
    private lateinit var defaultPlantList: List<Plant>
    private lateinit var customPlantList: MutableList<Plant>

    private val gson = Gson()

    private lateinit var imageViewIconLuz: ImageView
    private lateinit var imageViewIconTemperatura: ImageView
    private lateinit var imageViewIconHumedad: ImageView
    private lateinit var textViewUltimoDatoLuz: TextView
    private lateinit var textViewUltimoDatoTemperatura: TextView
    private lateinit var textViewUltimoDatoHumedad: TextView

    private lateinit var database: FirebaseDatabase
    private lateinit var testRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_principal)

        sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val button = findViewById<ImageButton>(R.id.PlantaGPT)

        imageViewIconLuz = findViewById(R.id.imageViewIconLuz)
        imageViewIconTemperatura = findViewById(R.id.imageViewIconTemperatura)
        imageViewIconHumedad = findViewById(R.id.imageViewIconHumedad)
        textViewUltimoDatoLuz = findViewById(R.id.textViewUltimoDatoLuz)
        textViewUltimoDatoTemperatura = findViewById(R.id.textViewUltimoDatoTemperatura)
        textViewUltimoDatoHumedad = findViewById(R.id.textViewUltimoDatoHumedad)

        database = FirebaseDatabase.getInstance()
        testRef = database.getReference("test")

        button.setOnClickListener {
            startActivity(Intent(this, PlantaGPT::class.java))
        }


        // Inicializar listas de plantas predeterminadas
        defaultPlantList = listOf(
            Plant("Tomate", "22-28°C, 60-70% humedad", R.drawable.ic_tomato, 25, 65, 12000),
            Plant("Lechuga", "15-20°C, 70-80% humedad", R.drawable.ic_lettuce, 18, 75, 8000),
            Plant("Pimiento", "20-30°C, 50-70% humedad", R.drawable.ic_pepper, 25, 60, 15000)
        )

        // Cargar plantas personalizadas desde SharedPreferences
        val customPlantsJson = sharedPref.getString("customPlants", null)
        customPlantList = if (customPlantsJson != null) {
            val type = object : TypeToken<MutableList<Plant>>() {}.type
            gson.fromJson(customPlantsJson, type)
        } else {
            mutableListOf()
        }

        // Verificar si es el primer uso de la aplicación
        if (sharedPref.getBoolean("isNewUser", true)) {
            showPlantSelectionDialog()
            sharedPref.edit().putBoolean("isNewUser", false).apply()
        }

        // Configurar botón para cambiar la planta
        findViewById<FloatingActionButton>(R.id.fabChangePlant).setOnClickListener {
            showPlantSelectionDialog()
        }

        // Cargar los datos de la planta actualmente seleccionada
        loadCurrentPlantData()

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

        // Actualizar TextViews con los últimos datos del sensor
        readLastSensorData()
    }

    private fun showPlantSelectionDialog() {
        val dialogBinding = DialogSelectPlantBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        // Combinar listas de plantas predeterminadas y personalizadas
        val allPlants = defaultPlantList + customPlantList

        dialogBinding.rvPlants.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvPlants.adapter = PlantAdapter(allPlants) { selectedPlant ->
            sharedPref.edit().putString("currentPlant", selectedPlant.name).apply()
            updatePlantUI(selectedPlant)
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
            .setCancelable(false)
            .create()

        // Configurar spinner con tipos de plantas comunes
        val plantTypes = arrayOf("Vegetal", "Fruta", "Hierba", "Flor", "Árbol", "Cactus/Suculenta", "Otro")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, plantTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerPlantType.adapter = adapter

        dialogBinding.btnSavePlant.setOnClickListener {
            val plantType = dialogBinding.spinnerPlantType.selectedItem.toString()
            val customName = dialogBinding.etPlantName.text.toString().trim()
            val lightText = dialogBinding.etLight.text.toString()
            val tempText = dialogBinding.etTemperature.text.toString()
            val humidityText = dialogBinding.etHumidity.text.toString()

            if (lightText.isEmpty() || tempText.isEmpty() || humidityText.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val light = lightText.toInt()
            val temp = tempText.toFloat().toInt()
            val humidity = humidityText.toInt()

            val plantName = if (customName.isNotEmpty()) customName else "Mi $plantType"
            val requirements = "$temp°C, $humidity% humedad"

            val newPlant = Plant(
                name = plantName,
                requirements = requirements,
                imageRes = when (plantType) {
                    "Vegetal" -> R.drawable.ic_tomato
                    "Fruta" -> R.drawable.ic_pepper
                    "Hierba" -> R.drawable.ic_lettuce
                    else -> R.drawable.ic_planta
                },
                idealTemp = temp,
                idealHumidity = humidity,
                idealLight = light
            )

            // Guardar la nueva planta personalizada
            customPlantList.add(newPlant)
            saveCustomPlants()

            // Establecer la nueva planta como la actual
            sharedPref.edit().putString("currentPlant", newPlant.name).apply()
            updatePlantUI(newPlant)
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
            showPlantSelectionDialog()
        }

        dialog.show()
    }

    private fun saveCustomPlants() {
        val json = gson.toJson(customPlantList)
        sharedPref.edit().putString("customPlants", json).apply()
    }

    private fun loadCurrentPlantData() {
        val currentPlantName = sharedPref.getString("currentPlant", defaultPlantList[0].name)
        val allPlants = defaultPlantList + customPlantList
        val currentPlant = allPlants.find { it.name == currentPlantName } ?: defaultPlantList[0]
        updatePlantUI(currentPlant)
    }

    private fun updatePlantUI(plant: Plant) {
        findViewById<ImageView>(R.id.ivPlant).setImageResource(plant.imageRes)
    }

    private fun readLastSensorData() {
        testRef.limitToLast(1).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (dataSnapshot in snapshot.children) {
                    val sensorData = dataSnapshot.getValue(SensorData::class.java)
                    sensorData?.let {
                        // Actualizar TextViews con los últimos datos
                        textViewUltimoDatoLuz.text = String.format("%.1f lux", it.luz ?: 0f)
                        textViewUltimoDatoTemperatura.text = String.format("%.1f°C", it.temperatura_ext ?: 0f)
                        textViewUltimoDatoHumedad.text = String.format("%.1f%%", it.humdedad_ext ?: 0f)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejar error
            }
        })
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
            holder.image.setImageResource(plant.imageRes)
            holder.name.text = plant.name
            holder.requirements.text = plant.requirements

            holder.itemView.setOnClickListener {
                onItemClick(plant)
            }
        }

        override fun getItemCount() = plants.size
    }

    data class Plant(
        val name: String,
        val requirements: String,
        val imageRes: Int,
        val idealTemp: Int,
        val idealHumidity: Int,
        val idealLight: Int
    ) : java.io.Serializable
}