package com.miriam.barcodetest.ui

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.AppSettings
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.Batch
import com.miriam.barcodetest.data.model.InventoryTransaction
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.data.model.NewTransaction
import com.miriam.barcodetest.data.repository.BatchesRepository
import com.miriam.barcodetest.data.repository.ProfileRepository
import com.miriam.barcodetest.data.repository.TransactionsRepository
import com.miriam.barcodetest.databinding.DialogAdjustBatchBinding
import com.miriam.barcodetest.databinding.FragmentItemDetailBinding
import com.miriam.barcodetest.databinding.ItemBatchRowBinding
import com.miriam.barcodetest.databinding.ItemTransactionRowBinding
import kotlinx.coroutines.launch

/**
 * כרטיס פריט: מה יש עכשיו, מאילו אצוות הוא מורכב ומתי הן פגות, וכל
 * ההיסטוריה שלו.
 *
 * שתי הרשימות כאן נבנות ישירות לתוך ScrollView ולא ב-RecyclerView, כי הן
 * קצרות מטבען (אצוות של פריט בודד) והמסך כולו נגלל כיחידה אחת - שתי רשימות
 * גוללות בתוך מסך גולל היו יוצרות התנהגות מבלבלת.
 */
class ItemDetailFragment : Fragment() {

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    private val batchesRepository = BatchesRepository()
    private val transactionsRepository = TransactionsRepository()
    private val profileRepository = ProfileRepository()

    private lateinit var itemId: String
    private lateinit var itemName: String
    private lateinit var itemUnit: String
    private var itemQuantity: Double = 0.0
    private var itemMinQuantity: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        itemId = args.getString(ARG_ID).orEmpty()
        itemName = args.getString(ARG_NAME).orEmpty()
        itemUnit = args.getString(ARG_UNIT).orEmpty()
        itemQuantity = args.getDouble(ARG_QUANTITY)
        itemMinQuantity = args.getDouble(ARG_MIN_QUANTITY)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        bindHeader()
        loadDetails()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ======================= תמונת המצב =======================

    private fun bindHeader() {
        val context = requireContext()
        val status = StockDisplay.statusOf(itemQuantity, itemMinQuantity)
        val color = StockDisplay.statusColor(context, status)

        binding.detailItemName.text = itemName
        binding.detailQuantity.text =
            "${StockDisplay.quantity(itemQuantity)} $itemUnit"
        binding.detailQuantity.setTextColor(color)
        binding.detailMinQuantity.text = getString(
            R.string.detail_min_quantity,
            StockDisplay.quantity(itemMinQuantity),
            itemUnit
        )
        binding.detailStatusBadge.text = StockDisplay.statusLabel(context, status)
        binding.detailStatusBadge.setTextColor(color)
    }

    // ======================= אצוות והיסטוריה =======================

    private fun loadDetails() {
        binding.detailProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val batchesResult = batchesRepository.getBatchesForItem(itemId)
            val transactionsResult = transactionsRepository.getTransactionsForItem(itemId)
            val namesResult = profileRepository.getProfileNames()

            if (_binding == null) return@launch
            binding.detailProgress.visibility = View.GONE

            val batches = (batchesResult as? Resource.Success)?.data.orEmpty()
            val transactions = (transactionsResult as? Resource.Success)?.data.orEmpty()
            val names = (namesResult as? Resource.Success)?.data.orEmpty()

            renderBatches(batches)
            renderHistory(transactions, names)
        }
    }

    /** רק אצוות שיש בהן מלאי בפועל, מהקרובה לתפוגה לרחוקה (אותו סדר של FEFO) */
    private fun renderBatches(batches: List<Batch>) {
        val container = binding.batchesContainer
        container.removeAllViews()

        val withStock = batches
            .filter { it.quantity > 0.0 }
            .sortedWith(compareBy(nullsLast<String>()) { it.expiryDate })

        binding.batchesEmpty.visibility = if (withStock.isEmpty()) View.VISIBLE else View.GONE

        val context = requireContext()
        val warningDays = AppSettings.expiryWarningDays(context)
        withStock.forEach { batch ->
            val row = ItemBatchRowBinding.inflate(layoutInflater, container, false)

            row.batchTitle.text = batch.batchNumber?.takeIf { it.isNotBlank() }
                ?.let { getString(R.string.detail_batch_number, it) }
                ?: getString(R.string.detail_batch_no_number)

            row.batchQuantity.text = StockDisplay.quantity(batch.quantity)

            // תאריך תפוגה: נצבע לפי הדחיפות, כי זה המידע שמנהלת המלאי
            // באמת מחפשת כשהיא פותחת את הכרטיס
            val neutralColor = ContextCompat.getColor(context, R.color.md_on_surface_variant)
            val formattedExpiry = StockDisplay.expiryDate(batch.expiryDate)
            val daysLeft = StockDisplay.daysUntilExpiry(batch.expiryDate)

            if (formattedExpiry == null) {
                row.batchExpiry.text = getString(R.string.detail_no_expiry)
                row.batchExpiry.setTextColor(neutralColor)
            } else {
                val base = getString(R.string.detail_expiry, formattedExpiry)
                when {
                    daysLeft != null && daysLeft < 0 -> {
                        row.batchExpiry.text = base + " · " + getString(R.string.detail_expired)
                        row.batchExpiry.setTextColor(StockDisplay.statusColor(context, "out"))
                    }
                    daysLeft != null && daysLeft <= warningDays -> {
                        row.batchExpiry.text = base + " · " +
                            getString(R.string.detail_expires_soon, daysLeft.toInt())
                        row.batchExpiry.setTextColor(StockDisplay.statusColor(context, "low"))
                    }
                    else -> {
                        row.batchExpiry.text = base
                        row.batchExpiry.setTextColor(neutralColor)
                    }
                }
            }

            val supplier = batch.supplier?.takeIf { it.isNotBlank() }
            if (supplier != null) {
                row.batchSupplier.text = getString(R.string.detail_supplier, supplier)
                row.batchSupplier.visibility = View.VISIBLE
            } else {
                row.batchSupplier.visibility = View.GONE
            }

            row.root.setOnClickListener { showAdjustDialog(batch) }

            container.addView(row.root)
        }
    }

    // ======================= תיקון ספירה =======================

    /**
     * תיקון ספירת מלאי לאצווה.
     *
     * המשתמשת מזינה את מה שספרה בפועל; ההפרש מחושב כאן ונרשם כתנועת
     * 'adjustment'. זו הדרך היחידה לתקן פער בין המדף למערכת: batches.quantity
     * הוא מטמון שרק הטריגר מעדכן (יש revoke בסכמה), ולכן כל תיקון חייב לעבור
     * דרך יומן התנועות - וכך גם נשאר תיעוד של מי תיקן ומתי.
     */
    private fun showAdjustDialog(batch: Batch) {
        val dialogBinding = DialogAdjustBatchBinding.inflate(layoutInflater)

        val batchLabel = batch.batchNumber?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.detail_batch_number, it) }
            ?: getString(R.string.detail_batch_no_number)
        dialogBinding.adjustBatchLabel.text = batchLabel
        dialogBinding.adjustRecorded.text = getString(
            R.string.adjust_recorded,
            StockDisplay.quantity(batch.quantity),
            itemUnit
        )
        dialogBinding.adjustCountedInput.setText(StockDisplay.quantity(batch.quantity))

        // ההפרש מתעדכן תוך כדי הקלדה, כדי שיהיה ברור מה עומד להירשם ביומן
        fun refreshDelta() {
            val counted = dialogBinding.adjustCountedInput.text.toString().toDoubleOrNull()
            dialogBinding.adjustDelta.text = when {
                counted == null || counted < 0.0 -> getString(R.string.adjust_invalid)
                counted == batch.quantity -> getString(R.string.adjust_no_change)
                else -> getString(
                    R.string.adjust_delta,
                    StockDisplay.signedQuantity("adjustment", counted - batch.quantity)
                )
            }
        }
        refreshDelta()
        dialogBinding.adjustCountedInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshDelta()
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.adjust_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.adjust_confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val counted = dialogBinding.adjustCountedInput.text.toString().toDoubleOrNull()
                if (counted == null || counted < 0.0) {
                    toast(getString(R.string.adjust_invalid))
                    return@setOnClickListener
                }
                val delta = counted - batch.quantity
                if (delta == 0.0) {
                    toast(getString(R.string.adjust_no_change))
                    return@setOnClickListener
                }
                dialog.dismiss()
                recordAdjustment(batch, delta)
            }
        }

        dialog.show()
    }

    private fun recordAdjustment(batch: Batch, delta: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            val transaction = NewTransaction(
                type = "adjustment",
                itemId = itemId,
                batchId = batch.id,
                quantity = delta,
                reason = getString(R.string.adjust_reason)
            )
            when (val result = transactionsRepository.recordTransaction(transaction)) {
                is Resource.Success -> {
                    toast(getString(R.string.adjust_saved))
                    // הכמות בכותרת מגיעה מהמסך הקודם ולכן כבר לא מדויקת אחרי
                    // התיקון. מרעננים אותה מסכום האצוות במקום להשאיר מספר ישן.
                    itemQuantity += delta
                    if (_binding != null) {
                        bindHeader()
                        loadDetails()
                    }
                }
                is Resource.Error -> toast(result.message)
                is Resource.Loading -> Unit
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun renderHistory(
        transactions: List<InventoryTransaction>,
        names: Map<String, String>
    ) {
        val container = binding.historyContainer
        container.removeAllViews()

        binding.historyEmpty.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE

        val context = requireContext()
        transactions.forEach { transaction ->
            val row = ItemTransactionRowBinding.inflate(layoutInflater, container, false)
            val color = StockDisplay.transactionColor(context, transaction.type)

            // ב-DB הכמות תמיד חיובית גם בהוצאה - הכיוון נשמר בשדה type ולא
            // בסימן המספר (ראו constraint transactions_quantity_sign בשלב 1).
            // לכן הסימן נגזר מסוג התנועה, חוץ מהתאמת ספירה שבה המספר עצמו
            // כבר נושא את הכיוון.
            row.txQuantity.text = StockDisplay.signedQuantity(transaction.type, transaction.quantity)
            row.txQuantity.setTextColor(color)

            row.txType.text = StockDisplay.transactionLabel(context, transaction.type)
            row.txType.setTextColor(color)

            val performer = transaction.performedBy?.let { id ->
                names[id] ?: getString(R.string.detail_unknown_user)
            }
            row.txMeta.text = listOfNotNull(
                StockDisplay.transactionTime(transaction.performedAt).takeIf { it.isNotEmpty() },
                transaction.reason?.takeIf { it.isNotBlank() },
                performer
            ).joinToString(" · ")

            container.addView(row.root)
        }
    }

    companion object {
        private const val ARG_ID = "item_id"
        private const val ARG_NAME = "item_name"
        private const val ARG_UNIT = "item_unit"
        private const val ARG_QUANTITY = "item_quantity"
        private const val ARG_MIN_QUANTITY = "item_min_quantity"

        fun newInstance(item: ItemStockStatus): ItemDetailFragment {
            val fragment = ItemDetailFragment()
            val args = Bundle()
            args.putString(ARG_ID, item.id)
            args.putString(ARG_NAME, item.name)
            args.putString(ARG_UNIT, item.unit)
            args.putDouble(ARG_QUANTITY, item.currentQuantity)
            args.putDouble(ARG_MIN_QUANTITY, item.minQuantity)
            fragment.arguments = args
            return fragment
        }
    }
}
