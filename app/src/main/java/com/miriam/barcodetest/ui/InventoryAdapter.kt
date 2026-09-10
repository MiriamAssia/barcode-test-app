package com.miriam.barcodetest.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.databinding.ItemInventoryRowBinding

/**
 * שורות טבלת המלאי. הקשה על שורה פותחת את כרטיס הפריט, ולחיצה ארוכה פותחת
 * את פעולות הפריט (כיבוי/הפעלה ומחיקה) - אותה מחווה כמו בחירת כמות במסך
 * ההוצאה, כדי שהיא תהיה צפויה.
 */
class InventoryAdapter(
    private val onItemClick: (ItemStockStatus) -> Unit,
    private val onItemLongPress: (ItemStockStatus) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.ItemViewHolder>() {

    private var items: List<ItemStockStatus> = emptyList()

    fun submit(newItems: List<ItemStockStatus>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder =
        ItemViewHolder(
            ItemInventoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ItemViewHolder(
        private val binding: ItemInventoryRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemStockStatus) {
            val context = binding.root.context

            binding.itemName.text = item.name
            binding.itemCategory.text =
                item.category?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.inventory_no_category)

            val status = StockDisplay.statusOf(item.currentQuantity, item.minQuantity)
            val color = StockDisplay.statusColor(context, status)

            binding.itemQuantity.text = StockDisplay.quantity(item.currentQuantity)
            binding.itemQuantity.setTextColor(color)

            binding.itemStatusBadge.text = StockDisplay.statusLabel(context, status)
            binding.itemStatusBadge.setTextColor(color)

            // פריט כבוי: תגית מפורשת ושורה מעומעמת. שני הערכים נקבעים בכל
            // bind ולא רק כשהם משתנים, אחרת מיחזור השורות ב-RecyclerView היה
            // גורם לפריט פעיל לרשת עמעום של פריט כבוי שגלל מעליו.
            binding.itemInactiveBadge.visibility =
                if (item.isActive) View.GONE else View.VISIBLE
            binding.root.alpha = if (item.isActive) 1f else 0.55f

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onItemLongPress(item)
                true
            }
        }
    }
}
