package com.miriam.barcodetest.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.miriam.barcodetest.R
import com.miriam.barcodetest.databinding.ItemReportRowBinding

/** שורות דוח הצריכה, ממוינות מהצריכה הגבוהה לנמוכה. */
class ReportsAdapter : RecyclerView.Adapter<ReportsAdapter.RowViewHolder>() {

    /**
     * @property share חלקו של הפריט בסך הצריכה, בין 0 ל-1
     * @property unit יחידת המידה. בפילוח לפי קטגוריה היא ריקה, כי קטגוריה
     *   יכולה לאגד פריטים ביחידות שונות ואז סכום היחידות לא אומר דבר אחיד.
     */
    data class Entry(
        val name: String,
        val amount: Double,
        val unit: String,
        val share: Double
    )

    private var entries: List<Entry> = emptyList()

    fun submit(newEntries: List<Entry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(
            ItemReportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    inner class RowViewHolder(
        private val binding: ItemReportRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: Entry) {
            val context = binding.root.context

            binding.reportName.text = entry.name
            binding.reportAmount.text = context.getString(
                R.string.reports_row_units,
                StockDisplay.quantity(entry.amount),
                entry.unit
            ).trim()
            binding.reportShare.text = context.getString(
                R.string.reports_row_share,
                Math.round(entry.share * 100).toInt()
            )

            // רוחב הפס נקבע במשקלים, ולכן עובד נכון בכל רוחב מסך בלי חישוב
            // ידני. מינימום קטן כדי שגם ערך זעיר עדיין ייראה כפס ולא ייעלם.
            val fill = entry.share.coerceIn(0.02, 1.0)
            setWeight(binding.reportBarFill, fill.toFloat())
            setWeight(binding.reportBarRest, (1.0 - fill).toFloat())
        }

        private fun setWeight(view: android.view.View, weight: Float) {
            val params = view.layoutParams as LinearLayout.LayoutParams
            params.weight = weight
            view.layoutParams = params
        }
    }
}
