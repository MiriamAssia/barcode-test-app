package com.miriam.barcodetest.data.repository

import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.ExpiringBatch
import com.miriam.barcodetest.data.model.ItemStockStatus

/**
 * מאחדת את שני סוגי ההתראות למקום אחד.
 *
 * אין כאן שאילתות חדשות: שני ה-views נבנו כבר בשלב 1 (schema.sql), וכל מה
 * שנשאר הוא לסנן מה באמת דורש טיפול. הסינון נעשה כאן ולא בכל מסך בנפרד,
 * כדי שהמונה בסרגל התחתון ומסך ההתראות עצמו יסכימו תמיד על אותו מספר.
 */
class AlertsRepository {

    private val itemsRepository = ItemsRepository()
    private val batchesRepository = BatchesRepository()

    /**
     * @param expiryWarningDays כמה ימים לפני התפוגה כבר מתריעים
     */
    suspend fun getAlerts(expiryWarningDays: Long): Resource<Alerts> {
        // כולל פריטים כבויים: אצווה שעומדת לפוג יכולה להשתייך לפריט שהוצא
        // משימוש, וההתראה עליה עדיין רלוונטית - המלאי הפיזי קיים. בלי הפריט
        // הכבוי כאן, הקשה על ההתראה הזו לא הייתה פותחת שום מסך.
        val statusResult = itemsRepository.getAllStockStatuses()
        if (statusResult is Resource.Error) return statusResult

        val expiringResult = batchesRepository.getExpiringBatches()
        if (expiringResult is Resource.Error) return expiringResult

        val statuses = (statusResult as? Resource.Success)?.data.orEmpty()
        val allExpiring = (expiringResult as? Resource.Success)?.data.orEmpty()

        // מלאי נמוך או אזל. הסטטוס מגיע מה-view, אבל מחושב כאן שוב מהכמות
        // והסף כדי שיהיה עקבי עם מה שמוצג בשאר המסכים.
        // פריט כבוי לא מתריע על מלאי נמוך - הפסיקו להשתמש בו בכוונה.
        val lowStock = statuses
            .filter { it.isActive && it.currentQuantity <= it.minQuantity }
            .sortedBy { it.currentQuantity }

        // אצוות שפגו כבר, או שיפוגו בתוך חלון ההתראה
        val expiring = allExpiring
            .filter { batch ->
                val days = batch.daysUntilExpiry
                days != null && days <= expiryWarningDays
            }
            .sortedBy { it.daysUntilExpiry ?: Int.MAX_VALUE }

        return Resource.Success(
            Alerts(
                lowStock = lowStock,
                expiring = expiring,
                allStatuses = statuses
            )
        )
    }

    /**
     * @property allStatuses כל הפריטים, כולל כבויים ולא רק אלה שבהתראה -
     *   נדרש כדי שהקשה על התראת תפוגה תוכל לפתוח את כרטיס הפריט המלא.
     */
    data class Alerts(
        val lowStock: List<ItemStockStatus>,
        val expiring: List<ExpiringBatch>,
        val allStatuses: List<ItemStockStatus>
    ) {
        val total: Int get() = lowStock.size + expiring.size
    }
}
