package com.appkungen.skredvarsel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.appkungen.varsomwidget.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.appkungen.skredvarsel.models.*

/**
 * ForecastDetailActivity with tabs for:
 * 1. Forecast details
 * 2. Widget settings
 * 3. Notifications
 */
class ForecastDetailActivity : AppCompatActivity() {

    private lateinit var forecasts: ArrayList<AvalancheReport>
    private var regionId: String = "3011"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forecast_detail)

        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.detail_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Get forecast data from intent
        val json = intent.getStringExtra("forecastJson")
        forecasts = if (json != null) {
            parseJsonToArrayList(json)
        } else {
            ArrayList()
        }

        // Get region ID
        if (forecasts.isNotEmpty()) {
            val widgetPrefs = WidgetPreferences(this)
            regionId = widgetPrefs.selectedRegion ?: "3011"
        }

        // Setup tabs and ViewPager
        setupTabs()
    }

    private fun setupTabs() {
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)

        // Create adapter with forecasts and region ID
        val adapter = ViewPagerAdapter(this, forecasts, regionId)
        viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "📊 Varsel"
                1 -> "⚙️ Innstillinger"
                2 -> "🔔 Varsler"
                else -> "Tab $position"
            }
        }.attach()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    /**
     * ViewPager adapter for the three tabs
     */
    private inner class ViewPagerAdapter(
        activity: AppCompatActivity,
        private val forecasts: ArrayList<AvalancheReport>,
        private val regionId: String
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ForecastFragment.newInstance(forecasts, regionId)
                1 -> WidgetSettingsFragment()
                2 -> NotificationsFragment()
                else -> ForecastFragment.newInstance(forecasts, regionId)
            }
        }
    }
}