package com.appkungen.wear

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import com.appkungen.wear.data.ForecastRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Enkel regionvelger på klokka.
 *
 * Viser en scrollbar liste med regioner (curved layout for rund skjerm).
 * Brukeren trykker på en region for å velge den.
 */
class WearSettingsActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repo: ForecastRepository
    private lateinit var recyclerView: WearableRecyclerView

    companion object {
        private const val TAG = "WearSettings"
    }

    data class RegionResponse(
        @SerializedName("Id") val id: String,
        @SerializedName("Name") val name: String,
        @SerializedName("TypeName") val typeName: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repo = ForecastRepository(this)

        recyclerView = WearableRecyclerView(this).apply {
            isEdgeItemsCenteringEnabled = true
            layoutManager = WearableLinearLayoutManager(this@WearSettingsActivity)
        }
        setContentView(recyclerView)

        loadRegions()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun loadRegions() {
        scope.launch {
            try {
                val regions = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .build()

                    val langCode = if (Locale.getDefault().language in listOf("nb", "no")) 1 else 2
                    val url = "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api/Region/$langCode"

                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: "[]"

                    val type = object : TypeToken<List<RegionResponse>>() {}.type
                    Gson().fromJson<List<RegionResponse>>(body, type)
                }

                // Filtrer til bare A-regioner (varslingsregioner) — samme filter som telefon-appen
                val avalancheRegions = regions
                    .filter { it.typeName == "A" }
                    .sortedBy { it.name }

                recyclerView.adapter = RegionAdapter(avalancheRegions) { region ->
                    repo.setSelectedRegion(region.id, region.name)
                    Toast.makeText(
                        this@WearSettingsActivity,
                        "Valgt: ${region.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Kunne ikke laste regioner", e)
                Toast.makeText(this@WearSettingsActivity, "Feil ved lasting", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Enkel adapter for regionliste på klokka
     */
    class RegionAdapter(
        private val regions: List<RegionResponse>,
        private val onClick: (RegionResponse) -> Unit
    ) : RecyclerView.Adapter<RegionAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(24, 16, 24, 16)
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val region = regions[position]
            holder.textView.text = region.name
            holder.itemView.setOnClickListener { onClick(region) }
        }

        override fun getItemCount() = regions.size
    }
}
