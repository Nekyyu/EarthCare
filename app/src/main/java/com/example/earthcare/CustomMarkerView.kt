package com.example.earthcare

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

class CustomMarkerView(
    context: Context,
    layoutResource: Int,
    private val unit: String,
    private val timestamps: List<String>
) : MarkerView(context, layoutResource) {
    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val tvDate: TextView = findViewById(R.id.tvDate)
    private val timeFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let {
            tvContent.text = String.format("%.1f%s", it.y, unit)
            
            val index = it.x.toInt()
            if (index >= 0 && index < timestamps.size) {
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timestamps[index])
                    date?.let { formattedDate ->
                        tvDate.text = timeFormatter.format(formattedDate)
                    }
                } catch (e: Exception) {
                    tvDate.text = timestamps[index]
                }
            }
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height - 10f)
    }
} 