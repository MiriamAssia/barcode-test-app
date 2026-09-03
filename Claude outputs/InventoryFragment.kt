package com.miriam.barcodetest.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.data.repository.ItemsRepository
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

        adapter = InventoryAdapter { item -> openItemDetail(item) }
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
            when (val result = itemsRepository.getStockStatuses()) {
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
            val matchesStatus = statusFilter == null ||
                StockDisplay.statusOf(item.currentQuantity, item.minQuantity) == statusFilter
            matchesQuery && matchesCategory && matchesStatus
        }

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

    // ======================= ניווט =======================

    private fun openItemDetail(item: ItemStockStatus) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, ItemDetailFragment.newInstance(item))
            .addToBackStack(null)
            .commit()
    }
}
