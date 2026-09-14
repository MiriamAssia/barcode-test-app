package com.miriam.barcodetest.data

import android.content.Context

/**
 * הגדרות מקומיות של האפליקציה, על גבי SharedPreferences.
 *
 * למה מקומי ולא בשרת: אלה העדפות תצוגה של המכשיר, לא נתוני מלאי. שמירה
 * ב-DB הייתה דורשת טבלה, מדיניות RLS וקריאת רשת בכל מסך שמשתמש בערך - הרבה
 * מנגנון בשביל מספר אחד. החיסרון המקובל: טאבלט חדש מתחיל מברירת המחדל.
 */
object AppSettings {

    private const val PREFS_NAME = "medtrack_settings"
    private const val KEY_EXPIRY_WARNING_DAYS = "expiry_warning_days"

    /** ברירת המחדל שהייתה קבועה בקוד עד עכשיו */
    const val DEFAULT_EXPIRY_WARNING_DAYS = 30L

    /** גבולות שפויים: פחות מיום אין לו משמעות, ומעל שנתיים הכל "עומד לפוג" */
    const val MIN_EXPIRY_WARNING_DAYS = 1L
    const val MAX_EXPIRY_WARNING_DAYS = 730L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * כמה ימים לפני התפוגה כבר מתריעים.
     *
     * הערך הזה משמש את מסך ההתראות, את המונה על סרגל הניווט ואת צביעת
     * האצוות בכרטיס הפריט - כולם קוראים מכאן, כדי שלא ייווצר מצב שבו מסך
     * אחד מסמן אצווה כדחופה והשני לא.
     */
    fun expiryWarningDays(context: Context): Long =
        prefs(context).getLong(KEY_EXPIRY_WARNING_DAYS, DEFAULT_EXPIRY_WARNING_DAYS)
            .coerceIn(MIN_EXPIRY_WARNING_DAYS, MAX_EXPIRY_WARNING_DAYS)

    fun setExpiryWarningDays(context: Context, days: Long) {
        prefs(context).edit()
            .putLong(
                KEY_EXPIRY_WARNING_DAYS,
                days.coerceIn(MIN_EXPIRY_WARNING_DAYS, MAX_EXPIRY_WARNING_DAYS)
            )
            .apply()
    }
}
