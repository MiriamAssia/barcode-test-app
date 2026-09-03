package com.miriam.barcodetest.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.Batch
import com.miriam.barcodetest.data.model.InventoryTransaction
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.data.repository.BatchesRepository
import com.miriam.barcodetest.data.repository.ProfileRepository
import com.miriam.barcodetest.data.repository.TransactionsRepository
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
                    daysLeft != null && daysLeft <= EXPIRY_WARNING_DAYS -> {
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

            container.addView(row.root)
        }
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

            // הכמות ב-DB כבר חתומה (שלילית ליציאה), אז מוצג הסימן כפי שהוא
            val sign = if (transaction.quantity > 0) "+" else ""
            row.txQuantity.text = "$sign${StockDisplay.quantity(transaction.quantity)}"
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

        /** כמה ימים לפני התפוגה כבר צובעים באזהרה */
        private const val EXPIRY_WARNING_DAYS = 30L

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
