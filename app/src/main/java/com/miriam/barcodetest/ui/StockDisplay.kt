package com.miriam.barcodetest.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.miriam.barcodetest.R
import java.math.BigDecimal
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

    /**
     * כמה ימים לפני התפוגה כבר מתריעים. משמש גם בכרטיס הפריט וגם במסך
     * ההתראות - שינוי כאן משנה את שניהם יחד, כדי שלא ייווצר מצב שבו מסך
     * אחד מסמן אצווה כדחופה והשני לא.
     */
    const val EXPIRY_WARNING_DAYS = 30L

    private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dayMonthYearTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /**
     * כמות לתצוגה: מספר שלם בלי נקודה עשרונית מיותרת (5 ולא 5.0), ושבר
     * מעוגל לשלוש ספרות - בדיוק כמו numeric(12,3) ב-DB.
     *
     * העיגול חיוני ולא קוסמטי: Double לא מייצג שברים עשרוניים במדויק, ולכן
     * סכום של כמויות (למשל בדוח הצריכה, שמסכם תנועות) היה מוצג כ-
     * "0.30000000000000004" במקום "0.3". העיגול לדיוק של העמודה מחזיר בדיוק
     * את המספר שנשמר, ו-BigDecimal מוודא שגם התצוגה לא תחזור לכתיב מדעי.
     */
    fun quantity(value: Double): String {
        val rounded = Math.round(value * 1000.0) / 1000.0
        if (rounded == rounded.toLong().toDouble()) return rounded.toLong().toString()
        return BigDecimal.valueOf(rounded).stripTrailingZeros().toPlainString()
    }

    /**
     * כמות עם סימן לתצוגה ביומן התנועות.
     *
     * ב-DB הכמות תמיד חיובית עבור 'in' ו-'out', והכיוון נשמר בשדה type.
     * רק ב-'adjustment' המספר עצמו יכול להיות שלילי (הפרש ספירה כלפי מטה),
     * ואז מציגים את הסימן שלו כמו שהוא.
     */
    fun signedQuantity(type: String, value: Double): String = when (type) {
        "in" -> "+" + quantity(value)
        "out" -> "-" + quantity(kotlin.math.abs(value))
        else -> if (value >= 0) "+" + quantity(value) else "-" + quantity(kotlin.math.abs(value))
    }

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
