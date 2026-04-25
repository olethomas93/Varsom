package com.appkungen.skredvarsel.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.appkungen.varsomwidget.BuildConfig
import com.appkungen.varsomwidget.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Map tab.
 *
 *  - Base layer:  Kartverket "topo" raster WMTS (norgeskart).
 *  - Overlay:     NVE Bratthet (steepness ≥30°) raster WMTS, ~50 % opacity.
 *  - User dot:    osmdroid's MyLocationNewOverlay (blue dot + accuracy ring).
 *  - Markers:     Recent snow/avalanche observations from Regobs (last 7 days, ≤200).
 *
 * Tap a marker → ObservationBottomSheet with summary; "Vis fullstendig" opens the
 * full registration on regobs.no in the browser.
 */
class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private lateinit var loading: ProgressBar
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val regobsRepo = RegobsRepository()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableMyLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required by osmdroid before MapView inflation. Sets the user-agent (Kartverket
        // requires a meaningful one) and caps the on-disk tile cache.
        val ctx = requireContext().applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 50L * 1024 * 1024 // 50 MB
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_map, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.map_view)
        loading = view.findViewById(R.id.map_loading)

        configureBaseMap()
        addBratthetOverlay()
        setupMyLocation()
        loadObservations()

        view.findViewById<MaterialButton>(R.id.center_on_me_button).setOnClickListener {
            centerOnMyLocation()
        }
    }

    private fun configureBaseMap() {
        mapView.setTileSource(KARTVERKET_TOPO)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(NORWAY_DEFAULT_ZOOM)
        mapView.controller.setCenter(NORWAY_CENTER)
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 17.0
    }

    private fun addBratthetOverlay() {
        val overlay = TilesOverlay(
            org.osmdroid.tileprovider.MapTileProviderBasic(requireContext(), NVE_BRATTHET),
            requireContext()
        ).apply {
            setColorFilter(null)
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            loadingLineColor = android.graphics.Color.TRANSPARENT
        }
        mapView.overlays.add(overlay)
    }

    private fun setupMyLocation() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            enableMyLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun enableMyLocation() {
        val provider = GpsMyLocationProvider(requireContext()).apply {
            locationUpdateMinTime = 5_000L
            locationUpdateMinDistance = 10f
        }
        val overlay = MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
            // First fix → recenter on the user, then stop following so the user can pan freely.
            runOnFirstFix {
                myLocation?.let { fix ->
                    mapView.post {
                        val center = GeoPoint(fix.latitude, fix.longitude)
                        mapView.controller.animateTo(center)
                        mapView.controller.setZoom(11.0)
                    }
                }
            }
        }
        mapView.overlays.add(overlay)
        myLocationOverlay = overlay
    }

    private fun centerOnMyLocation() {
        val fix = myLocationOverlay?.myLocation
        if (fix != null) {
            mapView.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
            if (mapView.zoomLevelDouble < 10.0) {
                mapView.controller.setZoom(11.0)
            }
        } else {
            // Permission missing or no fix yet — try (re)enabling.
            setupMyLocation()
        }
    }

    private fun loadObservations() {
        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = regobsRepo.recentSnowObservations()
            loading.visibility = View.GONE
            result
                .onSuccess { addObservationMarkers(it) }
                .onFailure { Log.w(TAG, "Could not load Regobs observations", it) }
        }
    }

    private fun addObservationMarkers(observations: List<RegobsObservation>) {
        val pinIcon: Drawable? =
            ResourcesCompat.getDrawable(resources, R.drawable.ic_map_observation, null)
        observations.forEach { obs ->
            val lat = obs.location?.latitude ?: return@forEach
            val lng = obs.location?.longitude ?: return@forEach
            val marker = Marker(mapView).apply {
                position = GeoPoint(lat, lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = pinIcon
                title = obs.location.forecastRegionName ?: obs.location.municipalName ?: ""
                setOnMarkerClickListener { _, _ ->
                    ObservationBottomSheet.newInstance(obs)
                        .show(parentFragmentManager, "obs-sheet")
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDetach()
    }

    companion object {
        private const val TAG = "MapFragment"

        // Roughly mid-Norway; zoom 5 covers the whole country.
        private val NORWAY_CENTER = GeoPoint(65.0, 14.0)
        private const val NORWAY_DEFAULT_ZOOM = 5.0

        /** Kartverket WMTS — public topographic raster used by norgeskart.no. */
        private val KARTVERKET_TOPO: OnlineTileSourceBase = object : OnlineTileSourceBase(
            "Kartverket-topo",
            4, 17,
            256,
            ".png",
            arrayOf("https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/"),
            "© Kartverket",
            TileSourcePolicy(
                2,
                TileSourcePolicy.FLAG_NO_BULK
                    or TileSourcePolicy.FLAG_NO_PREVENTIVE
                    or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                    or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "${baseUrl}$z/$y/$x.png"
            }
        }

        /** NVE bratthet/steepness layer (≥30° classes). Public WMTS. */
        private val NVE_BRATTHET: OnlineTileSourceBase = object : OnlineTileSourceBase(
            "NVE-bratthet",
            4, 16,
            256,
            ".png",
            arrayOf("https://nve.geodataonline.no/arcgis/rest/services/Bratthet/MapServer/tile/"),
            "© NVE",
            TileSourcePolicy(2, TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL)
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                // ArcGIS REST tile pattern: /tile/{z}/{y}/{x}
                return "${baseUrl}$z/$y/$x"
            }
        }
    }
}
