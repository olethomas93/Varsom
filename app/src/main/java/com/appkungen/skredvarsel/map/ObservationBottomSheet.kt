package com.appkungen.skredvarsel.map

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.appkungen.varsomwidget.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet shown when an observation marker is tapped.
 *
 * Top: date / region / observer.
 * Middle: short Regobs summary lines (one card per registration sub-type).
 * Bottom: button that opens the full observation page on regobs.no.
 */
class ObservationBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_observation, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val json = arguments?.getString(ARG_OBS_JSON) ?: run { dismiss(); return }
        val obs = Gson().fromJson(json, RegobsObservation::class.java) ?: run { dismiss(); return }

        view.findViewById<TextView>(R.id.obs_date).text =
            formatObsDate(obs.dtObsTime).uppercase(Locale.getDefault())

        view.findViewById<TextView>(R.id.obs_region).text =
            obs.location?.forecastRegionName
                ?: obs.location?.municipalName
                ?: "Ukjent område"

        view.findViewById<TextView>(R.id.obs_observer).text = listOfNotNull(
            obs.observer?.nickName?.takeIf { it.isNotBlank() },
            obs.observer?.observerGroupName?.takeIf { it.isNotBlank() }
        ).joinToString(" • ").ifEmpty { "Anonym observatør" }

        val container = view.findViewById<LinearLayout>(R.id.obs_summary_container)
        container.removeAllViews()
        val summaries = obs.summaries.orEmpty().filter { !it.summary.isNullOrBlank() }
        if (summaries.isEmpty()) {
            container.addView(plainSummaryRow("Ingen oppsummering tilgjengelig.", isItalic = true))
        } else {
            summaries.take(4).forEach { s ->
                container.addView(summaryRow(s))
            }
        }

        view.findViewById<MaterialButton>(R.id.obs_open_full).setOnClickListener {
            val url = obs.url?.takeIf { it.isNotBlank() }
                ?: RegobsConstants.observationUrl(obs.regId)
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun summaryRow(s: Summary): View {
        val ctx = requireContext()
        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        val title = TextView(ctx).apply {
            text = s.registrationName ?: "Observasjon"
            setTextColor(ctx.getColor(R.color.settings_text_tertiary))
            textSize = 11f
            letterSpacing = 0.06f
        }
        val body = TextView(ctx).apply {
            text = s.summary
            setTextColor(ctx.getColor(R.color.settings_text_primary))
            textSize = 13f
            setLineSpacing(2f, 1.1f)
            setPadding(0, dp(2), 0, 0)
        }
        wrapper.addView(title)
        wrapper.addView(body)
        return wrapper
    }

    private fun plainSummaryRow(text: String, isItalic: Boolean = false): View {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(requireContext().getColor(R.color.settings_text_secondary))
            textSize = 13f
            if (isItalic) setTypeface(typeface, android.graphics.Typeface.ITALIC)
        }
    }

    private fun formatObsDate(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            // Regobs returns "yyyy-MM-dd'T'HH:mm:ss" or with offset; trim to seconds.
            val pattern = if (iso.contains("+") || iso.endsWith("Z"))
                "yyyy-MM-dd'T'HH:mm:ssXXX" else "yyyy-MM-dd'T'HH:mm:ss"
            val date = SimpleDateFormat(pattern, Locale.US).parse(iso) ?: return iso.take(10)
            SimpleDateFormat("d. MMM yyyy 'kl.' HH:mm", Locale("no")).format(date)
        } catch (e: Exception) {
            iso.take(16).replace('T', ' ')
        }
    }

    private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_OBS_JSON = "obs_json"

        fun newInstance(observation: RegobsObservation): ObservationBottomSheet =
            ObservationBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_OBS_JSON, Gson().toJson(observation))
                }
            }
    }
}
