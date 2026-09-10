package com.miriam.barcodetest.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.data.repository.AlertsRepository
import com.miriam.barcodetest.databinding.FragmentAlertsBinding
import kotlinx.coroutines.launch

/**
 * מה דורש טיפול עכשיו: מלאי שנגמר או עומד להיגמר, ואצוות שעומדות לפוג.
 *
 * הקשה על כל התראה פותחת את כרטיס הפריט שנבנה בשלב 5, כדי שאפשר יהיה
 * לראות מיד את כל התמונה ולא רק את שורת ההתראה.
 */
class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    private val alertsRepository = AlertsRepository()
    private lateinit var adapter: AlertsAdapter

    /** נשמר כדי שהקשה על התראה תוכל להעביר את הפריט המלא לכרטיס */
    private var statusesById: Map<String, ItemStockStatus> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AlertsAdapter { itemId -> openItemDetail(itemId) }
        binding.alertsList.layoutManager = LinearLayoutManager(requireContext())
        binding.alertsList.adapter = adapter

        loadAlerts()
    }

    override fun onDestroyView() {
        binding.alertsList.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun loadAlerts() {
        val views = _binding ?: return
        views.alertsProgress.visibility = View.VISIBLE
        views.emptyState.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = alertsRepository.getAlerts(StockDisplay.EXPIRY_WARNING_DAYS)
            val current = _binding ?: return@launch
            current.alertsProgress.visibility = View.GONE

            when (result) {
                is Resource.Success -> {
                    val alerts = result.data
                    statusesById = alerts.allStatuses.associateBy { it.id }
                    adapter.submit(buildRows(alerts))
                    // מחזירים את טקסט ברירת המחדל: אם קודם הוצגה כאן שגיאה,
                    // בלי השורה הזו טעינה מוצלחת בלי התראות הייתה מציגה את
                    // הודעת השגיאה הישנה לצד סימן הווי הירוק.
                    current.emptyText.setText(R.string.alerts_none)
                    current.emptyState.visibility =
                        if (alerts.total == 0) View.VISIBLE else View.GONE
                }
                is Resource.Error -> {
                    // מרוקנים את הרשימה לפני הצגת השגיאה: מסך ההתראות הוא
                    // FrameLayout, ובלי זה שורות ההתראה הישנות נשארו גלויות
                    // מאחורי הודעת השגיאה וקראו כאילו הן עדכניות.
                    statusesById = emptyMap()
                    adapter.submit(emptyList())
                    current.emptyText.text = result.message
                    current.emptyState.visibility = View.VISIBLE
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** קבוצה ריקה לא מקבלת כותרת בכלל, כדי לא להציג "0" מיותר */
    private fun buildRows(alerts: AlertsRepository.Alerts): List<AlertsAdapter.Row> {
        val rows = mutableListOf<AlertsAdapter.Row>()

        if (alerts.lowStock.isNotEmpty()) {
            rows += AlertsAdapter.Row.Header(
                R.string.alerts_section_stock,
                alerts.lowStock.size
            )
            alerts.lowStock.forEach { rows += AlertsAdapter.Row.Stock(it) }
        }

        if (alerts.expiring.isNotEmpty()) {
            rows += AlertsAdapter.Row.Header(
                R.string.alerts_section_expiry,
                alerts.expiring.size
            )
            alerts.expiring.forEach { rows += AlertsAdapter.Row.Expiry(it) }
        }

        return rows
    }

    private fun openItemDetail(itemId: String) {
        val item = statusesById[itemId] ?: return
        parentFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, ItemDetailFragment.newInstance(item))
            .addToBackStack(null)
            .commit()
    }
}
