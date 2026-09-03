package com.miriam.barcodetest.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.databinding.ItemInventoryRowBinding

/** שורות טבלת המלאי. הקשה על שורה פותחת את כרטיס הפריט. */
class InventoryAdapter(
    private val onItemClick: (ItemStockStatus) -> Unit
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

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
