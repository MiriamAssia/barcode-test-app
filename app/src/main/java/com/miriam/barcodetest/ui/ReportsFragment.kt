package com.miriam.barcodetest.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.InventoryTransaction
import com.miriam.barcodetest.data.model.Item
import com.miriam.barcodetest.data.repository.ItemsRepository
import com.miriam.barcodetest.data.repository.TransactionsRepository
import com.miriam.barcodetest.databinding.FragmentReportsBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * דוח צריכה: כמה יצא מהמלאי בטווח נבחר, ואילו פריטים אחראים לרוב הצריכה.
 *
 * הדוח נבנה מתנועות ה-'out' בלבד. קליטות והתאמות ספירה לא נספרות כאן -
 * הן משנות את המלאי אבל אינן "שימוש", וערבוב שלהן היה מנפח את המספרים
 * ומטעה בתכנון הזמנות.
 *
 * החישוב נעשה במכשיר ולא בשרת: כמות התנועות בטווח של חודש במרפאה קטנה
 * היא זניחה, וכך אין צורך בפונקציה נוספת ב-DB או במיגרציה.
 */
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val transactionsRepository = TransactionsRepository()
    private val itemsRepository = ItemsRepository()
    private lateinit var adapter: ReportsAdapter

    private val displayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val zone: ZoneId = ZoneId.systemDefault()

    private var rangeFrom: LocalDate = LocalDate.now().minusDays(29)
    private var rangeTo: LocalDate = LocalDate.now()

    /** נשמר כדי שהחלפת פילוח לא תדרוש שליפה חוזרת מהשרת */
    private var loadedTransactions: List<InventoryTransaction> = emptyList()
    private var itemsById: Map<String, Item> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReportsAdapter()
        binding.reportList.layoutManager = LinearLayoutManager(requireContext())
        binding.reportList.adapter = adapter

        binding.rangeChips.setOnCheckedStateChangeListener { _, _ -> onRangeChanged() }
        binding.chipRangeCustom.setOnClickListener { pickCustomRange() }
        binding.groupChips.setOnCheckedStateChangeListener { _, _ -> render() }

        updateRangeLabel()
        loadReport()
    }

    override fun onDestroyView() {
        binding.reportList.adapter = null
        super.onDestroyView()
        _binding = null
    }

    // ======================= טווח תאריכים =======================

    private fun onRangeChanged() {
        val views = _binding ?: return
        val today = LocalDate.now()

        when (views.rangeChips.checkedChipId) {
            R.id.chipRange7 -> {
                rangeFrom = today.minusDays(6)
                rangeTo = today
            }
            R.id.chipRange30 -> {
                rangeFrom = today.minusDays(29)
                rangeTo = today
            }
            R.id.chipRangeMonth -> {
                rangeFrom = today.withDayOfMonth(1)
                rangeTo = today
            }
            else -> {
                // הטווח החופשי נבחר דרך בוררי התאריכים; אם עוד לא נבחר,
                // משאירים את הטווח הקודם עד שהמשתמש/ת יבחרו
                return
            }
        }

        updateRangeLabel()
        loadReport()
    }

    private fun pickCustomRange() {
        val startFrom = rangeFrom
        DatePickerDialog(
            requireContext(),
            { _, year, monthZeroBased, day ->
                val chosenFrom = LocalDate.of(year, monthZeroBased + 1, day)
                pickRangeEnd(chosenFrom)
            },
            startFrom.year,
            startFrom.monthValue - 1,
            startFrom.dayOfMonth
        ).show()
    }

    private fun pickRangeEnd(from: LocalDate) {
        val startTo = if (rangeTo.isBefore(from)) from else rangeTo
        DatePickerDialog(
            requireContext(),
            { _, year, monthZeroBased, day ->
                val chosenTo = LocalDate.of(year, monthZeroBased + 1, day)
                // אם נבחר סוף מוקדם מההתחלה, מחליפים ביניהם במקום להציג שגיאה
                if (chosenTo.isBefore(from)) {
                    rangeFrom = chosenTo
                    rangeTo = from
                } else {
                    rangeFrom = from
                    rangeTo = chosenTo
                }
                updateRangeLabel()
                loadReport()
            },
            startTo.year,
            startTo.monthValue - 1,
            startTo.dayOfMonth
        ).show()
    }

    private fun updateRangeLabel() {
        val views = _binding ?: return
        views.rangeLabel.text = getString(
            R.string.reports_range_label,
            rangeFrom.format(displayDate),
            rangeTo.format(displayDate)
        )
    }

    // ======================= טעינה =======================

    private fun loadReport() {
        val views = _binding ?: return
        views.reportProgress.visibility = View.VISIBLE
        views.emptyText.visibility = View.GONE

        // גבול תחתון: תחילת יום ההתחלה. גבול עליון: תחילת היום שאחרי יום
        // הסיום, כדי שיום הסיום ייכלל במלואו.
        val fromInstant = rangeFrom.atStartOfDay(zone).toInstant().toString()
        val toInstant = rangeTo.plusDays(1).atStartOfDay(zone).toInstant().toString()

        viewLifecycleOwner.lifecycleScope.launch {
            val transactionsResult =
                transactionsRepository.getOutTransactionsBetween(fromInstant, toInstant)
            val itemsResult = itemsRepository.getAllItems()

            val current = _binding ?: return@launch
            current.reportProgress.visibility = View.GONE

            // כשלון בכל אחת משתי השליפות מרוקן את הדוח לפני הצגת השגיאה.
            // בלי זה נשארו על המסך המספרים של הטווח הקודם לצד הודעת שגיאה,
            // והחלפת הפילוח אחר כך הייתה מציגה אותם שוב בלי שום סימן לתקלה.
            // בלי שמות הפריטים אין דוח קריא בכלל, ולכן גם הוא נחשב כשלון.
            val failure = (transactionsResult as? Resource.Error)?.message
                ?: (itemsResult as? Resource.Error)?.message
            if (failure != null) {
                clearReport()
                showEmpty(failure)
                return@launch
            }

            loadedTransactions = (transactionsResult as? Resource.Success)?.data.orEmpty()
            itemsById = (itemsResult as? Resource.Success)?.data.orEmpty().associateBy { it.id }

            render()
        }
    }

    // ======================= חישוב ותצוגה =======================

    private fun render() {
        val views = _binding ?: return

        val totalUnits = loadedTransactions.sumOf { it.quantity }
        views.summaryUnits.text = StockDisplay.quantity(totalUnits)
        views.summaryItems.text = loadedTransactions.map { it.itemId }.distinct().size.toString()
        views.summaryTransactions.text = loadedTransactions.size.toString()

        if (loadedTransactions.isEmpty()) {
            adapter.submit(emptyList())
            showEmpty(getString(R.string.reports_empty))
            return
        }
        views.emptyText.visibility = View.GONE

        val byCategory = views.groupChips.checkedChipId == R.id.chipGroupCategory
        adapter.submit(if (byCategory) buildByCategory(totalUnits) else buildByItem(totalUnits))
    }

    private fun buildByItem(totalUnits: Double): List<ReportsAdapter.Entry> =
        loadedTransactions
            .groupBy { it.itemId }
            .map { (itemId, rows) ->
                val item = itemsById[itemId]
                val amount = rows.sumOf { it.quantity }
                ReportsAdapter.Entry(
                    name = item?.name ?: itemId,
                    amount = amount,
                    unit = item?.unit.orEmpty(),
                    share = if (totalUnits > 0) amount / totalUnits else 0.0
                )
            }
            .sortedByDescending { it.amount }

    /**
     * בפילוח לפי קטגוריה לא מוצגת יחידת מידה: קטגוריה אחת יכולה לאגד
     * פריטים שנמדדים ביחידות שונות, וכותרת אחידה הייתה מטעה.
     */
    private fun buildByCategory(totalUnits: Double): List<ReportsAdapter.Entry> =
        loadedTransactions
            .groupBy { transaction ->
                itemsById[transaction.itemId]?.category?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.inventory_no_category)
            }
            .map { (category, rows) ->
                val amount = rows.sumOf { it.quantity }
                ReportsAdapter.Entry(
                    name = category,
                    amount = amount,
                    unit = "",
                    share = if (totalUnits > 0) amount / totalUnits else 0.0
                )
            }
            .sortedByDescending { it.amount }

    /** מרוקן את הדוח לגמרי - נתונים, אריחי הסיכום והרשימה */
    private fun clearReport() {
        val views = _binding ?: return
        loadedTransactions = emptyList()
        itemsById = emptyMap()
        adapter.submit(emptyList())
        views.summaryUnits.text = StockDisplay.quantity(0.0)
        views.summaryItems.text = "0"
        views.summaryTransactions.text = "0"
    }

    private fun showEmpty(message: String) {
        val views = _binding ?: return
        views.emptyText.text = message
        views.emptyText.visibility = View.VISIBLE
    }
}
