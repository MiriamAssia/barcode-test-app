package com.miriam.barcodetest.data.repository

import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.SupabaseClientProvider
import com.miriam.barcodetest.data.mapErrorToHebrewMessage
import com.miriam.barcodetest.data.model.Batch
import com.miriam.barcodetest.data.model.ExpiringBatch
import com.miriam.barcodetest.data.model.NewBatch
import io.github.jan.supabase.postgrest.postgrest

class BatchesRepository {

    private val postgrest = SupabaseClientProvider.client.postgrest

    /** כל האצוות של פריט מסוים - לכרטיס פריט / בחירת אצווה בקליטה */
    suspend fun getBatchesForItem(itemId: String): Resource<List<Batch>> = try {
        val batches = postgrest.from("batches")
            .select { filter { eq("item_id", itemId) } }
            .decodeList<Batch>()
        Resource.Success(batches)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /**
     * קליטת אצווה חדשה - מנהל/ת בלבד. הכמות ההתחלתית נכנסת בנפרד דרך
     * TransactionsRepository.recordTransaction עם type="in", לא כאן.
     */
    suspend fun addBatch(newBatch: NewBatch): Resource<Batch> = try {
        val created = postgrest.from("batches")
            .insert(newBatch) { select() }
            .decodeSingle<Batch>()
        Resource.Success(created)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /** אצוות עם מלאי שמתקרבות לתפוגה - view expiring_batches (למסך התראות, שלב 6) */
    suspend fun getExpiringBatches(): Resource<List<ExpiringBatch>> = try {
        val rows = postgrest.from("expiring_batches").select().decodeList<ExpiringBatch>()
        Resource.Success(rows)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /**
     * מאתרת אצווה מתאימה לקליטה, ואם אין כזו - יוצרת חדשה.
     *
     * למה לא פשוט ליצור אצווה חדשה בכל קליטה:
     * 1. ב-DB יש אינדקס ייחודי על (item_id, batch_number) - קליטה חוזרת של
     *    אותו מספר אצווה הייתה נכשלת עם שגיאת כפילות.
     * 2. גם בלי מספר אצווה, קליטה חוזרת של אותו תאריך תפוגה אמורה להצטבר
     *    לאותה שורה במקום לפזר את המלאי על עשרות אצוות זהות.
     *
     * הכמות עצמה לא נכתבת כאן - היא תמיד נגזרת מתנועת 'in' שנרשמת אחרי זה
     * (ראו TransactionsRepository.recordTransaction).
     */
    suspend fun findOrCreateBatch(
        itemId: String,
        batchNumber: String?,
        expiryDate: String?,
        supplier: String?
    ): Resource<Batch> = try {
        val normalizedNumber = batchNumber?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedSupplier = supplier?.trim()?.takeIf { it.isNotEmpty() }

        val existing = postgrest.from("batches")
            .select { filter { eq("item_id", itemId) } }
            .decodeList<Batch>()

        val match = if (normalizedNumber != null) {
            existing.firstOrNull { it.batchNumber == normalizedNumber }
        } else {
            existing.firstOrNull { it.batchNumber == null && it.expiryDate == expiryDate }
        }

        // אצווה קיימת עם אותו מספר אבל תאריך תפוגה אחר - לא מצרפים בשקט.
        // צירוף היה מבטל את התאריך שהוקלד עכשיו ומשאיר את המלאי החדש רשום
        // תחת תפוגה שגויה; במרפאה זו בדיוק הטעות שאסור לעשות בלי לשאול.
        val existingExpiry = match?.expiryDate

        if (match != null && normalizedNumber != null &&
            expiryDate != null && existingExpiry != null && existingExpiry != expiryDate
        ) {
            Resource.Error(
                "כבר קיימת אצווה $normalizedNumber לפריט הזה, עם תאריך תפוגה " +
                    "${asDisplayDate(existingExpiry)} במקום ${asDisplayDate(expiryDate)}. " +
                    "בדקי את מספר האצווה או את התאריך שהוקלד."
            )
        } else if (match != null) {
            Resource.Success(match)
        } else {
            val created = postgrest.from("batches")
                .insert(
                    NewBatch(
                        itemId = itemId,
                        batchNumber = normalizedNumber,
                        expiryDate = expiryDate,
                        supplier = normalizedSupplier
                    )
                ) { select() }
                .decodeSingle<Batch>()
            Resource.Success(created)
        }
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /**
     * תאריך מה-DB (yyyy-MM-dd) לתצוגה בעברית (dd/MM/yyyy).
     *
     * מכוון שזו שכבת הנתונים, לא משתמשים כאן ב-StockDisplay שיושב בשכבת
     * התצוגה - היפוך שלושת החלקים מספיק ולא יוצר תלות הפוכה בין השכבות.
     */
    private fun asDisplayDate(isoDate: String): String {
        val parts = isoDate.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else isoDate
    }
}
