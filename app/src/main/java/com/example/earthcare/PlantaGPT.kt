package com.example.earthcare


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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlantaGPT : AppCompatActivity() {

    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var recyclerViewMessages: RecyclerView
    private lateinit var buttonBack: ImageButton

    private val client = OkHttpClient()
    private val messages = mutableListOf<Message>()
    private lateinit var messageAdapter: MessageAdapter

    private val endpoint = "https://josep-max5lkqq-eastus2.cognitiveservices.azure.com/openai/deployments/EarthCareAI/chat/completions?api-version=2025-01-01-preview"
    private val apiKey = "D1sMgjonuufAn8WQHaEFD4peIQjAJ9JvTSbo4r7HvdwYtnRAPFGZJQQJ99BEACHYHv6XJ3w3AAAAACOGwjPH" // 🔒 Reemplázala desde configuración segura

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planta_gpt)

        userInput = findViewById(R.id.userInput)
        sendButton = findViewById(R.id.sendButton)
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages)
        buttonBack = findViewById(R.id.buttonBack)

        // Configurar RecyclerView
        messageAdapter = MessageAdapter(messages)
        recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        recyclerViewMessages.adapter = messageAdapter

        sendButton.setOnClickListener {
            val prompt = userInput.text.toString().trim()
            if (prompt.isNotEmpty()) {
                addMessage(Message(prompt, Sender.USER))
                userInput.text.clear()
                sendMessageToAzure(prompt)
            }
        }

        buttonBack.setOnClickListener { onBackPressed() }

        // Mensaje de bienvenida inicial (opcional)
        // addMessage(Message("¡Hola! ¿En qué puedo ayudarte con tu planta?", Sender.BOT))
    }

    private fun addMessage(message: Message) {
        runOnUiThread {
            messages.add(message)
            messageAdapter.notifyItemInserted(messages.size - 1)
            recyclerViewMessages.scrollToPosition(messages.size - 1)
        }
    }

    private fun sendMessageToAzure(prompt: String) {
        // Mensaje de contexto ("system") como en el Playground
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", "Eres un experto en cuidado de plantas. Responde de manera clara y breve.")

        // Construir el historial de mensajes para enviarlo a la API
        val messagesArray = JSONArray()
        messagesArray.put(systemMessage)

        // Añadir mensajes anteriores (excepto el mensaje de sistema inicial) y el nuevo mensaje del usuario
        messages.forEach { message ->
             val msgObj = JSONObject()
             msgObj.put("role", if (message.sender == Sender.USER) "user" else "assistant")
             msgObj.put("content", message.text)
             messagesArray.put(msgObj)
        }

        // Crear el objeto final JSON
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
                addMessage(Message("Error: ${e.message}", Sender.BOT))
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val botMessageText = try {
                    val jsonObj = JSONObject(body ?: "{}")
                    val choices = jsonObj.getJSONArray("choices")
                    val messageObj = choices.getJSONObject(0).getJSONObject("message")
                    messageObj.getString("content")
                } catch (e: Exception) {
                    "Error al interpretar respuesta."
                }
                addMessage(Message(botMessageText, Sender.BOT))
            }
        })
    }

    // Adaptador para el RecyclerView
    inner class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_SENT = 1
        private val VIEW_TYPE_RECEIVED = 2

        override fun getItemViewType(position: Int): Int {
            return if (messages[position].sender == Sender.USER) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_SENT) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val message = messages[position]
            if (holder.itemViewType == VIEW_TYPE_SENT) {
                (holder as SentMessageViewHolder).bind(message)
            } else {
                (holder as ReceivedMessageViewHolder).bind(message)
            }
        }

        override fun getItemCount() = messages.size

        inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val messageText: TextView = itemView.findViewById(R.id.textViewMessageSent)
            private val timestampText: TextView = itemView.findViewById(R.id.textViewTimestampSent)

            fun bind(message: Message) {
                messageText.text = message.text
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timestampText.text = timeFormat.format(Date(message.timestamp))
            }
        }

        inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val messageText: TextView = itemView.findViewById(R.id.textViewMessageReceived)
            private val timestampText: TextView = itemView.findViewById(R.id.textViewTimestampReceived)

            fun bind(message: Message) {
                messageText.text = message.text
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timestampText.text = timeFormat.format(Date(message.timestamp))
            }
        }
    }
}
