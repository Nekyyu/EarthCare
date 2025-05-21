import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.earthcare.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class PlantaGPT : AppCompatActivity() {

    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var responseText: TextView

    private val client = OkHttpClient()

    private val endpoint = "https://josep-max5lkqq-eastus2.cognitiveservices.azure.com/openai/deployments/EarthCareAI/chat/completions?api-version=2025-01-01-preview"
    private val apiKey = "D1sMgjonuufAn8WQHaEFD4peIQjAJ9JvTSbo4r7HvdwYtnRAPFGZJQQJ99BEACHYHv6XJ3w3AAAAACOGwjPH" // 🔒 Reemplázala desde configuración segura

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planta_gpt)

        userInput = findViewById(R.id.userInput)
        sendButton = findViewById(R.id.sendButton)
        responseText = findViewById(R.id.responseText)

        sendButton.setOnClickListener {
            val prompt = userInput.text.toString()
            sendMessageToAzure(prompt)
        }
    }

    private fun sendMessageToAzure(prompt: String) {
        val messageObj = JSONObject()
        messageObj.put("role", "user")
        messageObj.put("content", prompt)

        val messagesArray = JSONArray()
        messagesArray.put(messageObj)

        val json = JSONObject()
        json.put("messages", messagesArray)
        json.put("temperature", 0.7)

        val mediaType = "application/json".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    responseText.text = "Error: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val message = try {
                    val jsonObj = JSONObject(body ?: "{}")
                    val choices = jsonObj.getJSONArray("choices")
                    val messageObj = choices.getJSONObject(0).getJSONObject("message")
                    messageObj.getString("content")
                } catch (e: Exception) {
                    "Error al interpretar respuesta."
                }

                runOnUiThread {
                    responseText.text = message
                }
            }
        })
    }
}
