package com.appkungen.skredvarsel

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.appkungen.skredvarsel.map.MapActivity
import com.appkungen.skredvarsel.models.AvalancheReport
import com.appkungen.skredvarsel.repository.AvalancheForecastRepository
import com.appkungen.varsomwidget.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

/**
 * Hosts the forecast, widget settings, and notifications tabs.
 *
 * Two entry points:
 *   - Widget tap: receives a `forecastJson` intent extra with the cached forecast.
 *   - Launcher icon: no extras; we load the forecast ourselves via the repository
 *     using the user's stored region/coordinates (or the default region as a fallback).
 */
class ForecastDetailActivity : AppCompatActivity() {

    private var forecasts: ArrayList<AvalancheReport> = ArrayList()
    private var regionId: String = "3011"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forecast_detail)

        val toolbar = findViewById<MaterialToolbar>(R.id.detail_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        setupToolbar(toolbar)

        val widgetPrefs = WidgetPreferences(this)
        regionId = widgetPrefs.selectedRegion ?: "3011"

        val json = intent.getStringExtra("forecastJson")
        if (json != null) {
            forecasts = parseJsonToArrayList(json)
            setupTabs()
        } else {
            // Launched from app icon — load forecast ourselves.
            loadForecastThenSetupTabs(widgetPrefs)
        }
    }

    private fun loadForecastThenSetupTabs(widgetPrefs: WidgetPreferences) {
        findViewById<ProgressBar>(R.id.detail_loading)?.visibility = View.VISIBLE
        lifecycleScope.launch {
            val repo = AvalancheForecastRepository(this@ForecastDetailActivity)
            val coords = widgetPrefs.fetchedCoord?.let { parseStoredCoord(it) }
            when (val result = repo.getForecast(widgetPrefs.selectedRegion, coords)) {
                is AvalancheForecastRepository.Result.Success -> {
                    forecasts = ArrayList(result.data)
                }
                is AvalancheForecastRepository.Result.Error -> {
                    result.cachedData?.let { forecasts = ArrayList(it) }
                    Log.w("ForecastDetail", "Forecast load failed; showing cache=${result.cachedData != null}", result.exception)
                }
                else -> Unit
            }
            findViewById<ProgressBar>(R.id.detail_loading)?.visibility = View.GONE
            setupTabs()
        }
    }

    private fun parseStoredCoord(json: String): Pair<Double, Double>? = try {
        val obj = org.json.JSONObject(json)
        Pair(obj.getDouble("lat"), obj.getDouble("lng"))
    } catch (e: Exception) {
        null
    }

    private fun setupToolbar(toolbar: MaterialToolbar) {
        toolbar.menu.add(0, R.id.open_map, 0, "Kart").apply {
            setIcon(android.R.drawable.ic_dialog_map)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.open_map -> {
                    startActivity(Intent(this, MapActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabs() {
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)

        viewPager.adapter = ViewPagerAdapter(this, forecasts, regionId)

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

    private inner class ViewPagerAdapter(
        activity: AppCompatActivity,
        private val forecasts: ArrayList<AvalancheReport>,
        private val regionId: String
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> ForecastFragment.newInstance(forecasts, regionId)
            1 -> WidgetSettingsFragment()
            2 -> NotificationsFragment()
            else -> ForecastFragment.newInstance(forecasts, regionId)
        }
    }
}
