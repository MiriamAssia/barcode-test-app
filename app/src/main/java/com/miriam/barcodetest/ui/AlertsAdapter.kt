package com.miriam.barcodetest.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.model.ExpiringBatch
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.databinding.ItemAlertHeaderBinding
import com.miriam.barcodetest.databinding.ItemAlertRowBinding

/**
 * רשימת ההתראות: שתי קבוצות בתוך רשימה גוללת אחת.
 *
 * למה רשימה אחת ולא שתי רשימות נפרדות: המשתמש/ת רוצה לראות את כל מה שדורש
 * טיפול ברצף אחד. שתי רשימות עצמאיות היו מכריחות אותה לגלול כל אחת בנפרד.
 */
class AlertsAdapter(
    private val onAlertClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** שורה ברשימה: כותרת קבוצה, התראת מלאי, או התראת תפוגה */
    sealed class Row {
        data class Header(val titleRes: Int, val count: Int) : Row()
        data class Stock(val item: ItemStockStatus) : Row()
        data class Expiry(val batch: ExpiringBatch) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(newRows: List<Row>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        else -> TYPE_ALERT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemAlertHeaderBinding.inflate(inflater, parent, false))
        } else {
            AlertViewHolder(ItemAlertRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderViewHolder).bind(row)
            is Row.Stock -> (holder as AlertViewHolder).bindStock(row.item)
            is Row.Expiry -> (holder as AlertViewHolder).bindExpiry(row.batch)
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemAlertHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row.Header) {
            val context = binding.root.context
            binding.headerTitle.text = context.getString(row.titleRes)
            binding.headerCount.text = row.count.toString()
            binding.headerCount.setTextColor(StockDisplay.statusColor(context, "low"))
        }
    }

    inner class AlertViewHolder(
        private val binding: ItemAlertRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bindStock(item: ItemStockStatus) {
            val context = binding.root.context
            val isOut = item.currentQuantity <= 0.0
            val color = StockDisplay.statusColor(context, if (isOut) "out" else "low")

            binding.alertTitle.text = item.name
            binding.alertDetail.text = if (isOut) {
                context.getString(
                    R.string.alerts_stock_out,
                    StockDisplay.quantity(item.minQuantity)
                )
            } else {
                context.getString(
                    R.string.alerts_stock_detail,
                    StockDisplay.quantity(item.currentQuantity),
                    item.unit,
                    StockDisplay.quantity(item.minQuantity)
                )
            }

            binding.alertIcon.setImageResource(R.drawable.ic_inventory)
            binding.alertIcon.setColorFilter(color)
            binding.root.setOnClickListener { onAlertClick(item.id) }
        }

        fun bindExpiry(batch: ExpiringBatch) {
            val context = binding.root.context
            val days = batch.daysUntilExpiry ?: 0
            val isExpired = days < 0
            val color = StockDisplay.statusColor(context, if (isExpired) "out" else "low")

            val batchLabel = batch.batchNumber?.takeIf { it.isNotBlank() }
                ?.let { context.getString(R.string.alerts_expiry_batch, it) }
                ?: context.getString(R.string.alerts_expiry_no_batch)

            val timing = when {
                isExpired -> context.getString(R.string.alerts_expiry_expired, -days)
                days == 0 -> context.getString(R.string.alerts_expiry_today)
                else -> context.getString(R.string.alerts_expiry_days, days)
            }

            binding.alertTitle.text = batch.itemName
            binding.alertDetail.text =
                "$timing · $batchLabel · ${StockDisplay.quantity(batch.quantity)}"

            binding.alertIcon.setImageResource(R.drawable.ic_calendar)
            binding.alertIcon.setColorFilter(color)
            binding.root.setOnClickListener { onAlertClick(batch.itemId) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ALERT = 1
    }
}
