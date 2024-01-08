package com.northei.skredvarsel

import HttpClient
import com.northei.varsomwidget.R
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
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.RemoteViewsCompat.setViewBackgroundResource
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class AvalancheWarning(var DangerLevel :String )

class Region(var Name: String, var Id:String, var image:Int, var color:Int,var TypeName:String, var AvalancheWarningList:Array<AvalancheWarning>)
private class ViewHolder(view: View) {
    val areaName: TextView = view.findViewById(R.id.area)
    val risk_today:TextView = view.findViewById(R.id.risk_today)
    val risk_tomorrow:TextView = view.findViewById(R.id.risk_tomorrow)
    val risk_dayafter:TextView = view.findViewById(R.id.risk_dayafter)
    //val linearLayout: LinearLayout = view.findViewById(R.id.card)
    //val risk: TextView = view.findViewById(R.id.risk_number)
    //val image: ImageView = view.findViewById(R.id.risk_image)

}


class RegionAdapter(private val context: Context,private val regionList: ArrayList<Region>) :
    BaseAdapter(),Filterable {
    private val originalList = ArrayList(regionList)

    // Override the getView() method to customize the appearance of the items in the list
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // Get the current region from the list
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
        viewHolder.risk_today.text = region.AvalancheWarningList[0].DangerLevel;
        viewHolder.risk_tomorrow.text = region.AvalancheWarningList[1].DangerLevel;
        viewHolder.risk_dayafter.text = region.AvalancheWarningList[2].DangerLevel;
        //viewHolder.risk.text = region.risk
       viewHolder.risk_today.setBackgroundResource(
           getRiskColor(region.AvalancheWarningList[0].DangerLevel)
       )
        viewHolder.risk_tomorrow.setBackgroundResource(
            getRiskColor(region.AvalancheWarningList[1].DangerLevel)
        )
        viewHolder.risk_dayafter.setBackgroundResource(
            getRiskColor(region.AvalancheWarningList[2].DangerLevel)
        )
        //viewHolder.linearLayout.setBackgroundResource(region.color)


        return view!!
    }

    fun getRiskColor(riskLevel: String): Int {

        val colorRes = when (riskLevel) {
            "1" -> R.drawable.warning_1
            "2" -> R.drawable.warning_2
            "3" -> R.drawable.warning_3
            "4" -> R.drawable.warning_4
            else -> R.drawable.warning_00
        }

        return colorRes
    }
    override fun getItem(position: Int): Any {
        return regionList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return regionList.size
    }

    fun getTodayAndDayAfterTomorrow(): Pair<String, String> {
        // Get yesterday's date
        val yesterdayCalendar = Calendar.getInstance()
        //yesterdayCalendar.add(Calendar.DAY_OF_MONTH)
        val yesterdayDate = yesterdayCalendar.time
        val yesterdayFormatted = SimpleDateFormat("yyyy-MM-dd").format(yesterdayDate)

        // Get the date for the day after tomorrow
        val dayAfterTomorrowCalendar = Calendar.getInstance()
        dayAfterTomorrowCalendar.add(Calendar.DAY_OF_MONTH, 2)
        val dayAfterTomorrowDate = dayAfterTomorrowCalendar.time
        val dayAfterTomorrowFormatted = SimpleDateFormat("yyyy-MM-dd").format(dayAfterTomorrowDate)

        return Pair(yesterdayFormatted, dayAfterTomorrowFormatted)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList = ArrayList<Region>()

                if (constraint.isNullOrEmpty()) {
                    filteredList.addAll(originalList)
                } else {
                    val filterPattern = constraint.toString().toLowerCase().trim()

                    for (region in originalList) {
                        if (region.Name.toLowerCase().contains(filterPattern)) {
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
    private val client = OkHttpClient()
    var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    var locationRequest: LocationRequest? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
   //  var locationManager:LocationManager = TODO()
    // var locationListener:LocationListener

    override fun onCreate(icicle: Bundle?) {

        super.onCreate(icicle)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_configure)
        title = "Select Region"

        val httpClient = HttpClient(client)
        val regionId = 3011
        var arrayList: ArrayList<Region> = ArrayList()
        val url = "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api/RegionSummary/detail/1/"
        // There may be multiple widgets active, so update all of them

        httpClient.makeRequest(url) { response, error ->
            if (error != null) {
                //Log.d("HTTPCLIENT", "ERROR: $error")
            } else {
                //Log.d("HTTPCLIENT", "Response: $response")
                val list: ArrayList<Region>? = response?.let { parseJsonToArrayList(it) }
                if (list != null) {
                    runOnUiThread {
                    try {
                        val filteredList1 = ArrayList<Region>()
                        for (region in list){

                            if(region.TypeName.equals("A")){

                                filteredList1.add(region)
                            }
                        }
                        val searchView:SearchView = findViewById(R.id.search_bar)


                        val listView: ListView = findViewById(R.id.list_region)
                        val myPosition:TextView = findViewById(R.id.minposisjon)

                        myPosition.setOnClickListener{
                            getCurrentLocation()

                        }




                        val adapter = RegionAdapter(this,filteredList1)

                        listView.adapter = adapter
                        listView.setOnItemClickListener { parent, view, position, id ->
                            // Get the region object that was clicked
                            val region = parent.getItemAtPosition(position) as Region

                            val resultIntent = Intent()
                            resultIntent.putExtra("selectedRegion",region.Id)
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()

                            sendBroadcastToWidget(region.Id)

                            // Do something with the region object, e.g. show a Toast message
                            Toast.makeText(this, "Clicked on ${region.Name}", Toast.LENGTH_SHORT).show()
                        }










                        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String?): Boolean {
                                return false
                            }

                            override fun onQueryTextChange(newText: String?): Boolean {
                                // if query text is change in that case we
                                // are filtering our adapter with
                                // new text on below line.
                                adapter.filter.filter(newText)
                                return true
                            }
                        })

                    }catch (e:Exception){
                        Log.d("CATCHEX", "Response $e")
                    }
                    }
                }


            }
        }














        // Find the widget id from the intent.
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
    }

    private fun getCurrentLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(
                    this@Configure,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (isGPSEnabled()) {
                    Log.d("POSITIONX", "ERROR: finner posisjon")
                    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location : Location? ->

                            if (location != null) {
                                var coords = "{\"lat\":\"${location.latitude}\",\"lng\":\"${location.longitude}\"}"
                                val resultIntent = Intent()
                                resultIntent.putExtra("fetchedCoord",coords)
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                                sendBroadcastCordToWidget(coords)
                            }
                        }

                    }
                 else {
                    turnOnGPS()
                }
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }
    }

    private fun isGPSEnabled():Boolean {
        var locationManager: LocationManager? = null
        var isEnabled = false;

        if (locationManager == null) {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager;
        }

        isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        return isEnabled;
    }


    private fun sendBroadcastToWidget(region: String) {
        val intent = Intent(this, varsom::class.java)
        intent.action ="setRegion"
        intent.putExtra("selectedRegion", region)
        sendBroadcast(intent)
    }
    private fun sendBroadcastCordToWidget(coords: String) {
        val intent = Intent(this, varsom::class.java)
        intent.action = "setCoord"
        intent.putExtra("fetchedCoord", coords)
        sendBroadcast(intent)
    }

    fun parseJsonToArrayList(jsonString: String): ArrayList<Region> {
        val gson = Gson()
        val listType = object : TypeToken<ArrayList<Region>>() {}.type
        return gson.fromJson(jsonString, listType)
    }
    private fun turnOnGPS() {
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest!!)
        builder.setAlwaysShow(true)
        val result: Task<LocationSettingsResponse> = LocationServices.getSettingsClient(
            applicationContext
        )
            .checkLocationSettings(builder.build())
        result.addOnCompleteListener(OnCompleteListener<LocationSettingsResponse?> { task ->
            try {
                val response = task.getResult(ApiException::class.java)
                Toast.makeText(this@Configure, "GPS is already tured on", Toast.LENGTH_SHORT)
                    .show()
            } catch (e: ApiException) {
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        val resolvableApiException = e as ResolvableApiException
                        resolvableApiException.startResolutionForResult(this@Configure, 2)
                    } catch (ex: SendIntentException) {
                        ex.printStackTrace()
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {}
                }
            }
        })
    }
}