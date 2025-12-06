package com.appkungen.skredvarsel

import HttpClient
import com.appkungen.varsomwidget.R
import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import com.appkungen.skredvarsel.models.*




class Region(
    var Name: String,
    var Id: String,
    var image: Int,
    var color: Int,
    var TypeName: String,
    var AvalancheWarningList: Array<AvalancheWarning>
)

private class ViewHolder(view: View) {
    val areaName: TextView = view.findViewById(R.id.area)
    val risk_today: TextView = view.findViewById(R.id.risk_today)
    val risk_tomorrow: TextView = view.findViewById(R.id.risk_tomorrow)
    val risk_dayafter: TextView = view.findViewById(R.id.risk_dayafter)
}

class RegionAdapter(
    private val context: Context,
    private val regionList: ArrayList<Region>
) : BaseAdapter(), Filterable {
    private val originalList = ArrayList(regionList)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        val viewHolder: ViewHolder
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_view, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            viewHolder = view.tag as ViewHolder
        }

        val region = regionList[position]

        viewHolder.areaName.text = region.Name

        if (region.AvalancheWarningList.isNotEmpty()) {
            viewHolder.risk_today.text = region.AvalancheWarningList.getOrNull(0)?.DangerLevel ?: "-"
            viewHolder.risk_tomorrow.text = region.AvalancheWarningList.getOrNull(1)?.DangerLevel ?: "-"
            viewHolder.risk_dayafter.text = region.AvalancheWarningList.getOrNull(2)?.DangerLevel ?: "-"

            viewHolder.risk_today.setBackgroundResource(
                DangerLevelMapper.getWarningDrawable(region.AvalancheWarningList[0].DangerLevel)
            )
            viewHolder.risk_tomorrow.setBackgroundResource(
                DangerLevelMapper.getWarningDrawable(
                    region.AvalancheWarningList.getOrNull(1)?.DangerLevel ?: "0"
                )
            )
            viewHolder.risk_dayafter.setBackgroundResource(
                DangerLevelMapper.getWarningDrawable(
                    region.AvalancheWarningList.getOrNull(2)?.DangerLevel ?: "0"
                )
            )
        }

        return view!!
    }

    override fun getItem(position: Int): Any = regionList[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getCount(): Int = regionList.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList = ArrayList<Region>()

                if (constraint.isNullOrEmpty()) {
                    filteredList.addAll(originalList)
                } else {
                    val filterPattern = constraint.toString().lowercase().trim()
                    for (region in originalList) {
                        if (region.Name.lowercase().contains(filterPattern)) {
                            filteredList.add(region)
                        }
                    }
                }

                val results = FilterResults()
                results.values = filteredList
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                @Suppress("UNCHECKED_CAST")
                regionList.clear()
                regionList.addAll(results?.values as List<Region>)
                notifyDataSetChanged()
            }
        }
    }
}

class Configure : AppCompatActivity() {
    private val httpClient = HttpClient()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var locationRequest: LocationRequest? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var widgetPreferences: WidgetPreferences
    private lateinit var adapter: RegionAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_configure)

        // Set up the toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.configure_toolbar)
        setSupportActionBar(toolbar)

        title = "Select Region"

        widgetPreferences = WidgetPreferences(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupUI()
        loadRegions()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.configure_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notifications -> {
                val intent = Intent(this, NotificationSettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI() {
        val searchView: SearchView = findViewById(R.id.search_bar)
        val listView: ListView = findViewById(R.id.list_region)
        val myPosition: TextView = findViewById(R.id.minposisjon)

        myPosition.setOnClickListener {
            getCurrentLocation()
        }

        listView.setOnItemClickListener { parent, _, position, _ ->
            val region = parent.getItemAtPosition(position) as Region

            widgetPreferences.selectedRegion = region.Id

            val resultIntent = Intent()
            resultIntent.putExtra("selectedRegion", region.Id)
            setResult(Activity.RESULT_OK, resultIntent)

            sendBroadcastToWidget(region.Id)
            finish()

            Toast.makeText(this, "Selected ${region.Name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRegions() {
        val (todayDate, dayAfterTomorrowDate) = getTodayAndDayAfterTomorrow()
        val url = "${WidgetConstants.API_BASE_URL}/RegionSummary/detail/${WidgetConstants.LANG_CODE_NORWEGIAN}/$todayDate/$dayAfterTomorrowDate"

        httpClient.makeRequest(url) { response, error ->
            if (error != null) {
                Log.e("Configure", "Failed to load regions", error)
                runOnUiThread {
                    Toast.makeText(this, "Failed to load regions", Toast.LENGTH_SHORT).show()
                }
            } else {
                response?.let { parseAndDisplayRegions(it) }
            }
        }
    }

    private fun parseAndDisplayRegions(jsonResponse: String) {
        val list: ArrayList<Region>? = parseJsonToArrayList(jsonResponse)
        if (list != null) {
            runOnUiThread {
                try {
                    val filteredList = list.filter { it.TypeName == "A" }

                    val searchView: SearchView = findViewById(R.id.search_bar)
                    val listView: ListView = findViewById(R.id.list_region)

                    adapter = RegionAdapter(this, ArrayList(filteredList))
                    listView.adapter = adapter

                    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean = false

                        override fun onQueryTextChange(newText: String?): Boolean {
                            adapter.filter.filter(newText)
                            return true
                        }
                    })
                } catch (e: Exception) {
                    Log.e("Configure", "Error displaying regions", e)
                    Toast.makeText(this, "Error displaying regions", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (isGPSEnabled()) {
                Log.d("Configure", "Getting current location")
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            val coords = """{"lat":"${location.latitude}","lng":"${location.longitude}"}"""

                            widgetPreferences.fetchedCoord = coords

                            val resultIntent = Intent()
                            resultIntent.putExtra("fetchedCoord", coords)
                            setResult(Activity.RESULT_OK, resultIntent)

                            sendBroadcastCoordToWidget(coords)
                            finish()
                        } else {
                            Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Configure", "Failed to get location", e)
                        Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                    }
            } else {
                turnOnGPS()
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun isGPSEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun sendBroadcastToWidget(region: String) {
        val intent = Intent(this, VarsomWidgetProvider::class.java)
        intent.action = WidgetConstants.ACTION_SET_REGION
        intent.putExtra("selectedRegion", region)
        sendBroadcast(intent)
    }

    private fun sendBroadcastCoordToWidget(coords: String) {
        val intent = Intent(this, VarsomWidgetProvider::class.java)
        intent.action = WidgetConstants.ACTION_SET_COORD
        intent.putExtra("fetchedCoord", coords)
        sendBroadcast(intent)
    }

    private fun parseJsonToArrayList(jsonString: String): ArrayList<Region> {
        val gson = Gson()
        val listType = object : TypeToken<ArrayList<Region>>() {}.type
        return gson.fromJson(jsonString, listType)
    }

    private fun getTodayAndDayAfterTomorrow(): Pair<String, String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val today = Calendar.getInstance()
        val todayFormatted = dateFormat.format(today.time)

        val dayAfterTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 2)
        }
        val dayAfterTomorrowFormatted = dateFormat.format(dayAfterTomorrow.time)

        return Pair(todayFormatted, dayAfterTomorrowFormatted)
    }

    private fun turnOnGPS() {
        locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest!!)
            .setAlwaysShow(true)

        val result: Task<LocationSettingsResponse> = LocationServices
            .getSettingsClient(applicationContext)
            .checkLocationSettings(builder.build())

        result.addOnCompleteListener { task ->
            try {
                task.getResult(ApiException::class.java)
                Toast.makeText(this, "GPS is already turned on", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        try {
                            val resolvableApiException = e as ResolvableApiException
                            resolvableApiException.startResolutionForResult(
                                this,
                                WidgetConstants.GPS_ENABLE_REQUEST
                            )
                        } catch (ex: SendIntentException) {
                            Log.e("Configure", "Failed to resolve GPS settings", ex)
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        Toast.makeText(this, "Location settings unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.cancelAll()
    }
}