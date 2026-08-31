package com.miriam.barcodetest.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.AppScope
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.data.repository.ItemsRepository
import com.miriam.barcodetest.data.repository.TransactionsRepository
import com.miriam.barcodetest.databinding.DialogQuantityBinding
import com.miriam.barcodetest.databinding.FragmentCheckoutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * מסך הוצאת מלאי מהירה - המסך המרכזי של הצוות הקליני.
 *
 * מנגנון הביטול: הקשה על פריט לא נשלחת מיד לשרת. היא נכנסת ל"המתנה" של
 * ארבע שניות שבמהלכן מוצגת הודעת ביטול. רק אם לא ביטלו, ההוצאה נשלחת
 * בפועל. כך יומן התנועות נשאר נקי - מה שבוטל פשוט לא נרשם מעולם, במקום
 * להתמלא בזוגות של הוצאה וביטול שיקשו על הדוחות בהמשך.
 *
 * כדי שלא תאבד הוצאה אמיתית, ההמתנה נשלחת מיד (בלי לחכות לטיימר) בכל מצב
 * שבו עוזבים את המסך - מעבר טאב, יציאה מהאפליקציה, או הקשה על פריט אחר.
 * השליחה עצמה רצה ב-AppScope כדי שתסתיים גם אחרי שהמסך כבר נהרס.
 */
class CheckoutFragment : Fragment() {

    private var _binding: FragmentCheckoutBinding? = null
    private val binding get() = _binding!!

    private val itemsRepository = ItemsRepository()
    private val transactionsRepository = TransactionsRepository()

    private lateinit var adapter: CheckoutAdapter
    private lateinit var appContext: Context

    /** כל הפריטים כפי שהתקבלו מהשרת, לפני סינון החיפוש */
    private var allItems: List<ItemStockStatus> = emptyList()

    /** הוצאה שממתינה בחלון הביטול. תמיד אחת לכל היותר. */
    private var pendingItem: ItemStockStatus? = null
    private var pendingQuantity: Double = 0.0

    private val handler = Handler(Looper.getMainLooper())
    private val flushRunnable = Runnable { flushPending() }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { code -> handleScannedBarcode(code) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ההקשר של האפליקציה, כדי שנוכל להציג שגיאת שליחה גם אם המסך כבר נסגר
        appContext = requireContext().applicationContext
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCheckoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CheckoutAdapter(
            onItemTap = { item -> queueCheckout(item, 1.0) },
            onItemLongPress = { item -> showQuantityDialog(item) }
        )
        binding.itemsList.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsList.adapter = adapter

        binding.scanButton.setOnClickListener { startScan() }
        binding.undoButton.setOnClickListener { undoPending() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })

        loadItems()
    }

    override fun onPause() {
        super.onPause()
        // עוזבים את המסך - שולחים מיד את מה שממתין, בלי לחכות לסוף הטיימר
        flushPending()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(flushRunnable)
        binding.itemsList.adapter = null
        super.onDestroyView()
        _binding = null
    }

    // ======================= טעינת הרשימה =======================

    private fun loadItems(showSpinner: Boolean = true) {
        val views = _binding ?: return
        if (showSpinner) views.listProgress.visibility = View.VISIBLE
        views.emptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = itemsRepository.getStockStatuses()) {
                is Resource.Success -> {
                    allItems = result.data
                    _binding?.listProgress?.visibility = View.GONE
                    applyFilter()
                }
                is Resource.Error -> {
                    _binding?.listProgress?.visibility = View.GONE
                    showEmptyMessage(result.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun applyFilter() {
        val views = _binding ?: return
        val query = views.searchInput.text.toString().trim()

        val filtered = if (query.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.name.contains(query, ignoreCase = true) }
        }

        adapter.submit(filtered, currentPendingMap())

        when {
            allItems.isEmpty() -> showEmptyMessage(getString(R.string.checkout_empty))
            filtered.isEmpty() -> showEmptyMessage(getString(R.string.checkout_no_results))
            else -> views.emptyText.visibility = View.GONE
        }
    }

    private fun showEmptyMessage(message: String) {
        val views = _binding ?: return
        views.emptyText.text = message
        views.emptyText.visibility = View.VISIBLE
    }

    // ======================= סריקת ברקוד =======================

    private fun startScan() {
        val options = ScanOptions()
            .setPrompt(getString(R.string.checkout_scan_prompt))
            .setBeepEnabled(true)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    /**
     * ה-view של סטטוס המלאי לא כולל את עמודת הברקוד, ולכן מתרגמים קודם
     * ברקוד -> פריט דרך טבלת items, ואז מסננים את הרשימה לפי שם הפריט.
     */
    private fun handleScannedBarcode(code: String) {
        if (_binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = itemsRepository.findItemByBarcode(code.trim())) {
                is Resource.Success -> {
                    val item = result.data
                    if (item != null) {
                        _binding?.searchInput?.setText(item.name)
                    } else {
                        toast(getString(R.string.checkout_scan_not_found))
                    }
                }
                is Resource.Error -> toast(result.message)
                is Resource.Loading -> Unit
            }
        }
    }

    // ======================= בחירת כמות =======================

    private fun showQuantityDialog(item: ItemStockStatus) {
        val dialogBinding = DialogQuantityBinding.inflate(layoutInflater)
        dialogBinding.quantityItemName.text = item.name

        dialogBinding.quantityMinus.setOnClickListener {
            changeDialogQuantity(dialogBinding, -1.0)
        }
        dialogBinding.quantityPlus.setOnClickListener {
            changeDialogQuantity(dialogBinding, 1.0)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.quantity_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.quantity_dialog_confirm) { _, _ ->
                val quantity = dialogBinding.quantityInput.text.toString().toDoubleOrNull() ?: 0.0
                if (quantity > 0.0) queueCheckout(item, quantity)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun changeDialogQuantity(dialogBinding: DialogQuantityBinding, delta: Double) {
        val current = dialogBinding.quantityInput.text.toString().toDoubleOrNull() ?: 0.0
        val next = (current + delta).coerceAtLeast(1.0)
        dialogBinding.quantityInput.setText(formatQuantity(next))
    }

    // ======================= הוצאה, המתנה וביטול =======================

    private fun currentPendingMap(): Map<String, Double> {
        val item = pendingItem ?: return emptyMap()
        return mapOf(item.id to pendingQuantity)
    }

    /**
     * מכניסה הוצאה לחלון ההמתנה. הקשות חוזרות על אותו פריט מצטברות לרישום
     * אחד ומאתחלות את הטיימר; מעבר לפריט אחר שולח קודם את הקודם.
     */
    private fun queueCheckout(item: ItemStockStatus, quantity: Double) {
        val existing = pendingItem
        if (existing != null && existing.id != item.id) {
            flushPending()
        }

        if (pendingItem?.id == item.id) {
            pendingQuantity += quantity
        } else {
            pendingItem = item
            pendingQuantity = quantity
        }

        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, UNDO_WINDOW_MS)

        showUndoBar(item, pendingQuantity)
        applyFilter()
    }

    private fun showUndoBar(item: ItemStockStatus, quantity: Double) {
        val views = _binding ?: return
        views.undoText.text = getString(
            R.string.undo_message,
            formatQuantity(quantity),
            item.unit
        )
        views.undoBar.visibility = View.VISIBLE
    }

    private fun undoPending() {
        handler.removeCallbacks(flushRunnable)
        pendingItem = null
        pendingQuantity = 0.0
        _binding?.undoBar?.visibility = View.GONE
        applyFilter()
    }

    /**
     * שולחת בפועל את ההוצאה הממתינה. רצה ב-AppScope ולא בתחום החיים של
     * המסך, כדי שהשליחה תושלם גם אם המסך נהרס באותו רגע.
     */
    private fun flushPending() {
        val item = pendingItem ?: return
        val quantity = pendingQuantity

        pendingItem = null
        pendingQuantity = 0.0
        handler.removeCallbacks(flushRunnable)
        _binding?.undoBar?.visibility = View.GONE

        val ctx = appContext
        AppScope.io.launch {
            val result = transactionsRepository.checkoutItem(item.id, quantity)
            withContext(Dispatchers.Main) {
                if (result is Resource.Error) {
                    Toast.makeText(ctx, result.message, Toast.LENGTH_LONG).show()
                }
                // רענון מהשרת כדי שהמספרים על המסך ישקפו את המצב האמיתי
                if (isAdded && _binding != null) loadItems(showSpinner = false)
            }
        }
    }

    // ======================= עזר =======================

    private fun toast(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }

    private fun formatQuantity(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private companion object {
        /** אורך חלון הביטול, בהתאם לעיצוב */
        const val UNDO_WINDOW_MS = 4000L
    }
}
