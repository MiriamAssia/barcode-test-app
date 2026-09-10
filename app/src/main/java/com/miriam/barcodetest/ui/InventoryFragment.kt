package com.miriam.barcodetest.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.data.repository.BatchesRepository
import com.miriam.barcodetest.data.repository.ItemsRepository
import com.miriam.barcodetest.data.repository.TransactionsRepository
import com.miriam.barcodetest.databinding.FragmentInventoryBinding
import kotlinx.coroutines.launch

/**
 * רשימת המלאי המלאה - המסך של מנהלת המלאי.
 *
 * שלושת המסננים (חיפוש חופשי, מצב מלאי, קטגוריה) עובדים יחד ומצטברים.
 * הסינון כולו מקומי על הנתונים שכבר נטענו, ולא שאילתה חוזרת לשרת - כך
 * ההקלדה מסננת מיידית בלי המתנה לרשת.
 */
class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    private val itemsRepository = ItemsRepository()
    private val transactionsRepository = TransactionsRepository()
    private val batchesRepository = BatchesRepository()
    private lateinit var adapter: InventoryAdapter

    private var allItems: List<ItemStockStatus> = emptyList()

    /** null = כל הקטגוריות */
    private var selectedCategory: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = InventoryAdapter(
            onItemClick = { item -> openItemDetail(item) },
            onItemLongPress = { item -> showItemActions(item) }
        )
        binding.itemsList.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsList.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilters()
        })

        binding.statusChips.setOnCheckedStateChangeListener { _, _ -> applyFilters() }

        loadItems()
    }

    override fun onResume() {
        super.onResume()
        // חוזרים לרשימה אחרי כרטיס פריט או אחרי הוצאה במסך אחר - רענון כדי
        // שהכמויות שמוצגות יהיו העדכניות
        if (allItems.isNotEmpty()) loadItems(showSpinner = false)
    }

    override fun onDestroyView() {
        binding.itemsList.adapter = null
        super.onDestroyView()
        _binding = null
    }

    // ======================= טעינה =======================

    private fun loadItems(showSpinner: Boolean = true) {
        val views = _binding ?: return
        if (showSpinner) views.listProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            // כולל פריטים כבויים: הם נשארים ברשימה כדי שאפשר יהיה להפעיל אותם
            // מחדש או למחוק אותם, אבל יורדים לתחתית (ראו המיון ב-applyFilters).
            when (val result = itemsRepository.getAllStockStatuses()) {
                is Resource.Success -> {
                    allItems = result.data
                    _binding?.listProgress?.visibility = View.GONE
                    buildCategoryChips()
                    applyFilters()
                }
                is Resource.Error -> {
                    _binding?.listProgress?.visibility = View.GONE
                    showEmptyMessage(result.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * צ'יפ לכל קטגוריה שקיימת בפועל בנתונים, ועוד אחד ל"כל הקטגוריות".
     * נבנה מחדש בכל טעינה כי קטגוריה חדשה יכולה להיווצר במסך הקליטה.
     */
    private fun buildCategoryChips() {
        val views = _binding ?: return
        val categories = allItems
            .mapNotNull { it.category?.takeIf { name -> name.isNotBlank() } }
            .distinct()
            .sorted()

        // אם הקטגוריה שנבחרה כבר לא קיימת בנתונים (הפריט האחרון בה נמחק או
        // שונה), חוזרים ל"כל הקטגוריות". בלי זה אף צ'יפ לא היה מסומן והרשימה
        // הייתה מסתננת לכלום בלי שום הסבר.
        val chosen = selectedCategory
        if (chosen != null && chosen !in categories) {
            selectedCategory = null
        }

        views.categoryChips.removeAllViews()
        addCategoryChip(getString(R.string.inventory_category_all), null)
        categories.forEach { category -> addCategoryChip(category, category) }
    }

    private fun addCategoryChip(label: String, value: String?) {
        val views = _binding ?: return
        val chip = layoutInflater.inflate(R.layout.item_filter_chip, views.categoryChips, false) as Chip
        chip.text = label
        chip.isChecked = value == selectedCategory
        chip.setOnClickListener {
            selectedCategory = value
            applyFilters()
        }
        views.categoryChips.addView(chip)
    }

    // ======================= סינון =======================

    private fun applyFilters() {
        val views = _binding ?: return
        val query = views.searchInput.text.toString().trim()

        val statusFilter = when (views.statusChips.checkedChipId) {
            R.id.chipStatusLow -> "low"
            R.id.chipStatusOut -> "out"
            else -> null
        }

        val filtered = allItems.filter { item ->
            val matchesQuery = query.isEmpty() || item.name.contains(query, ignoreCase = true)
            val matchesCategory = selectedCategory == null || item.category == selectedCategory
            // מסנני "נמוך"/"אזל" מיועדים למה שדורש הזמנה, ולכן חלים על פריטים
            // פעילים בלבד - אחרת כל פריט שהוצא משימוש היה מציף את הרשימה הזו.
            val matchesStatus = statusFilter == null ||
                (item.isActive &&
                    StockDisplay.statusOf(item.currentQuantity, item.minQuantity) == statusFilter)
            matchesQuery && matchesCategory && matchesStatus
        }.sortedWith(compareBy({ !it.isActive }, { it.name }))

        adapter.submit(filtered)
        views.resultCount.text = getString(R.string.inventory_count, filtered.size)

        when {
            allItems.isEmpty() -> showEmptyMessage(getString(R.string.inventory_empty))
            filtered.isEmpty() -> showEmptyMessage(getString(R.string.inventory_no_results))
            else -> views.emptyText.visibility = View.GONE
        }
    }

    private fun showEmptyMessage(message: String) {
        val views = _binding ?: return
        views.emptyText.text = message
        views.emptyText.visibility = View.VISIBLE
    }

    // ======================= פעולות על פריט =======================

    /** תפריט הפעולות שנפתח בלחיצה ארוכה על שורה */
    private fun showItemActions(item: ItemStockStatus) {
        val toggleLabel = if (item.isActive) {
            getString(R.string.item_action_deactivate)
        } else {
            getString(R.string.item_action_activate)
        }
        val actions = arrayOf(toggleLabel, getString(R.string.item_action_delete))

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setItems(actions) { _, which ->
                if (which == 0) toggleActive(item) else confirmDelete(item)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleActive(item: ItemStockStatus) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = itemsRepository.setItemActive(item.id, !item.isActive)) {
                is Resource.Error -> toast(result.message)
                is Resource.Success -> {
                    toast(
                        getString(
                            if (item.isActive) R.string.item_deactivated else R.string.item_activated,
                            item.name
                        )
                    )
                    if (_binding != null) loadItems(showSpinner = false)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * מחיקה לצמיתות. לפני האישור נספרת ההיסטוריה בפועל, כדי שהאזהרה תגיד
     * מספר אמיתי ולא נוסח כללי - ההבדל בין "יימחקו גם נתונים" לבין "יימחקו
     * גם 47 תנועות" הוא ההבדל בין אזהרה שמדלגים עליה לאחת שעוצרים בגללה.
     */
    private fun confirmDelete(item: ItemStockStatus) {
        viewLifecycleOwner.lifecycleScope.launch {
            val transactions = transactionsRepository.getTransactionsForItem(item.id)
            val batches = batchesRepository.getBatchesForItem(item.id)
            if (_binding == null) return@launch

            val txCount = (transactions as? Resource.Success)?.data?.size ?: 0
            val batchCount = (batches as? Resource.Success)?.data?.size ?: 0

            val message = if (txCount == 0 && batchCount == 0) {
                getString(R.string.item_delete_confirm_clean, item.name)
            } else {
                getString(R.string.item_delete_confirm_history, item.name, txCount, batchCount)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.item_delete_title)
                .setMessage(message)
                .setPositiveButton(R.string.item_action_delete) { _, _ -> deleteItem(item) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun deleteItem(item: ItemStockStatus) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = itemsRepository.deleteItem(item.id)) {
                is Resource.Error -> toast(result.message)
                is Resource.Success -> {
                    toast(getString(R.string.item_deleted, item.name))
                    if (_binding != null) loadItems(showSpinner = false)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_LONG).show()
    }

    // ======================= ניווט =======================

    private fun openItemDetail(item: ItemStockStatus) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, ItemDetailFragment.newInstance(item))
            .addToBackStack(null)
            .commit()
    }
}
