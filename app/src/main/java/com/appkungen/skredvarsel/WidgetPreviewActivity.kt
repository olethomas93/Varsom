package com.appkungen.skredvarsel

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appkungen.varsomwidget.R

class WidgetPreviewActivity : AppCompatActivity() {

    private lateinit var forecastList: ArrayList<AvalanceReport>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_preview)

        val json = intent.getStringExtra("forecastJson") ?: return

        forecastList = parseJsonToArrayList(json)

        // Show today's forecast (index 1, same as widget)
        val today = forecastList[1]
        fillMediumWidget(today)
    }

    private fun fillMediumWidget(r: AvalanceReport) {

        findViewById<TextView>(R.id.risk_number).text = r.DangerLevel
        findViewById<TextView>(R.id.area_name).text = r.RegionName
        findViewById<TextView>(R.id.widget_current_day).text = parseDayName(r.ValidFrom)
        findViewById<TextView>(R.id.date).text = parseDateString(r.ValidFrom)
        findViewById<TextView>(R.id.risk_description).text = r.MainText

        findViewById<ImageView>(R.id.risk_image)
            .setImageResource(DangerLevelMapper.getLevelIcon(r.DangerLevel))

        // Set background color for root based on danger level
        val bg = DangerLevelMapper.getWarningDrawable(r.DangerLevel)
        findViewById<android.view.View>(R.id.root).setBackgroundResource(bg)
    }
}
