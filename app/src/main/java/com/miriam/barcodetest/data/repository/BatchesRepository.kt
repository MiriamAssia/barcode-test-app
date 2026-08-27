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

        if (match != null) {
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
}
