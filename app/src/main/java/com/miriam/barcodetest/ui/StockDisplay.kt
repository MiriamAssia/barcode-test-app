package com.miriam.barcodetest.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.miriam.barcodetest.R
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * פונקציות תצוגה משותפות לכל מסכי המלאי.
 *
 * הן יושבות כאן ולא בתוך מסך מסוים כי אותה כמות, אותו תאריך ואותו מצב מלאי
 * מוצגים בכמה מסכים, וחשוב שייראו זהים בכולם.
 */
object StockDisplay {

    private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dayMonthYearTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /** מספרים שלמים מוצגים בלי נקודה עשרונית מיותרת: 5 ולא 5.0 */
    fun quantity(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /**
     * מצב המלאי. הסטטוס מגיע מה-view item_stock_status, אבל כשיש הוצאות
     * שממתינות בחלון הביטול הכמות בפועל שונה - ולכן מחושב כאן מחדש.
     */
    fun statusOf(quantity: Double, minQuantity: Double): String = when {
        quantity <= 0.0 -> "out"
        quantity <= minQuantity -> "low"
        else -> "ok"
    }

    fun statusLabel(context: Context, status: String): String = when (status) {
        "out" -> context.getString(R.string.status_out)
        "low" -> context.getString(R.string.status_low)
        else -> context.getString(R.string.status_ok)
    }

    fun statusColor(context: Context, status: String): Int {
        val colorRes = when (status) {
            "out" -> R.color.md_error
            "low" -> R.color.md_warning
            else -> R.color.md_tertiary
        }
        return ContextCompat.getColor(context, colorRes)
    }

    /** תאריך תפוגה מה-DB (yyyy-MM-dd) בפורמט קריא */
    fun expiryDate(raw: String?): String? = raw?.let {
        runCatching { LocalDate.parse(it).format(dayMonthYear) }.getOrDefault(it)
    }

    /** מספר הימים עד התפוגה. שלילי = כבר פג. null = אין תאריך תפוגה. */
    fun daysUntilExpiry(raw: String?): Long? = raw?.let {
        runCatching {
            LocalDate.parse(it).toEpochDay() - LocalDate.now().toEpochDay()
        }.getOrNull()
    }

    /**
     * חותמת זמן של תנועה. ב-DB היא timestamptz, ולכן מומרת לאזור הזמן של
     * המכשיר כדי שהשעה שתוצג תהיה השעה שבה הפעולה באמת בוצעה מבחינת המשתמש/ת.
     */
    fun transactionTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(dayMonthYearTime)
        }.getOrDefault(raw)
    }

    fun transactionLabel(context: Context, type: String): String = when (type) {
        "in" -> context.getString(R.string.tx_in)
        "out" -> context.getString(R.string.tx_out)
        else -> context.getString(R.string.tx_adjustment)
    }

    fun transactionColor(context: Context, type: String): Int {
        val colorRes = when (type) {
            "in" -> R.color.md_tertiary
            "out" -> R.color.md_primary
            else -> R.color.md_warning
        }
        return ContextCompat.getColor(context, colorRes)
    }
}
