package com.miriam.barcodetest.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.databinding.ItemCheckoutRowBinding

/**
 * מציג את רשימת הפריטים במסך ההוצאה.
 *
 * הכמות שמוצגת היא "הכמות הצפויה": הכמות מהשרת פחות הוצאות שעדיין ממתינות
 * בחלון הביטול. כך המספר על המסך תמיד תואם למה שהמשתמש/ת בדיוק עשו, גם
 * לפני שהרישום נשלח בפועל.
 */
class CheckoutAdapter(
    private val onItemTap: (ItemStockStatus) -> Unit,
    private val onItemLongPress: (ItemStockStatus) -> Unit
) : RecyclerView.Adapter<CheckoutAdapter.ItemViewHolder>() {

    private var items: List<ItemStockStatus> = emptyList()

    /** קיזוזים ממתינים לפי מזהה פריט - הוצאות שטרם נשלחו לשרת */
    private var pendingDeductions: Map<String, Double> = emptyMap()

    fun submit(newItems: List<ItemStockStatus>, pending: Map<String, Double>) {
        items = newItems
        pendingDeductions = pending
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemCheckoutRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ItemViewHolder(
        private val binding: ItemCheckoutRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemStockStatus) {
            val pending = pendingDeductions[item.id] ?: 0.0
            val effectiveQuantity = (item.currentQuantity - pending).coerceAtLeast(0.0)

            binding.itemName.text = item.name
            binding.itemStock.text = binding.root.context.getString(
                R.string.checkout_stock_available,
                formatQuantity(effectiveQuantity),
                item.unit
            )

            // צביעה לפי מצב המלאי: אדום כשאזל, כתום כשנמוך, אפור כשתקין.
            // הסטטוס מגיע מוכן מה-view item_stock_status ולא מחושב כאן.
            val colorRes = when {
                effectiveQuantity <= 0.0 -> R.color.md_error
                item.status == "low" || effectiveQuantity <= item.minQuantity -> R.color.md_warning
                else -> R.color.md_on_surface_variant
            }
            binding.itemStock.setTextColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )

            // כשאין מלאי אין מה להוציא - השורה נשארת גלויה אבל לא פעילה
            val available = effectiveQuantity > 0.0
            binding.root.isEnabled = available
            binding.minusLabel.alpha = if (available) 1f else 0.3f
            binding.root.alpha = if (available) 1f else 0.6f

            binding.root.setOnClickListener {
                if (available) onItemTap(item)
            }
            binding.root.setOnLongClickListener {
                if (available) {
                    onItemLongPress(item)
                    true
                } else {
                    false
                }
            }
        }
    }

    private companion object {
        fun formatQuantity(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
