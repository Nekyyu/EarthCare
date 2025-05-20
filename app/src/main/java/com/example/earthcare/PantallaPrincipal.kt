package com.example.earthcare

import android.app.Dialog
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.earthcare.databinding.DialogSelectPlantBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PantallaPrincipal : AppCompatActivity() {

    private lateinit var sharedPref: SharedPreferences
    private val plantList = listOf(
        Plant("Tomate", "22-28°C, 60-70% humedad", R.drawable.ic_tomato, 25, 65, 12000),
        Plant("Lechuga", "15-20°C, 70-80% humedad", R.drawable.ic_lettuce, 18, 75, 8000),
        Plant("Pimiento", "20-30°C, 50-70% humedad", R.drawable.ic_pepper, 25, 60, 15000)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_principal)

        sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        // Verificar si es nuevo usuario
        if (sharedPref.getBoolean("isNewUser", true)) {
            showPlantSelectionDialog()
            sharedPref.edit().putBoolean("isNewUser", false).apply()
        }

        // Configurar botón para cambiar planta
        findViewById<FloatingActionButton>(R.id.fabChangePlant).setOnClickListener {
            showPlantSelectionDialog()
        }

        // Cargar datos de la planta actual
        loadCurrentPlantData()
    }

    private fun showPlantSelectionDialog() {
        val dialogBinding = DialogSelectPlantBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.rvPlants.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvPlants.adapter = PlantAdapter(plantList) { selectedPlant ->
            sharedPref.edit().putString("currentPlant", selectedPlant.name).apply()
            updatePlantUI(selectedPlant)
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun loadCurrentPlantData() {
        val currentPlantName = sharedPref.getString("currentPlant", plantList[0].name)
        val currentPlant = plantList.find { it.name == currentPlantName } ?: plantList[0]
        updatePlantUI(currentPlant)
    }

    private fun updatePlantUI(plant: Plant) {
        findViewById<ImageView>(R.id.ivPlant).setImageResource(plant.imageRes)
        findViewById<TextView>(R.id.tvTemperature).text = "${plant.idealTemp}°C"
        findViewById<TextView>(R.id.tvHumidity).text = "${plant.idealHumidity}%"
        findViewById<TextView>(R.id.tvLight).text = "${plant.idealLight} lux"
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
    )
}