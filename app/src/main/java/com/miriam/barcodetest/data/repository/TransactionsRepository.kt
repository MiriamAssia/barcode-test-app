package com.miriam.barcodetest.data.repository

import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.SupabaseClientProvider
import com.miriam.barcodetest.data.mapErrorToHebrewMessage
import com.miriam.barcodetest.data.model.InventoryTransaction
import com.miriam.barcodetest.data.model.NewTransaction
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TransactionsRepository {

    private val postgrest = SupabaseClientProvider.client.postgrest

    /**
     * הוצאת מלאי מהירה (מסך שלב 3) - קוראת לפונקציית ה-DB checkout_item, שבוחרת
     * אוטומטית אצווה לפי FEFO ורושמת את התנועה בעצמה. זו הדרך היחידה שבה צוות
     * קליני מוציא מלאי - RLS לא מאפשר להם להוסיף ל-transactions ישירות (שלב 1).
     *
     * שמות הפרמטרים חייבים להתאים בדיוק לחתימה ב-schema.sql:
     * checkout_item(p_item_id, p_quantity, p_reason)
     */
    suspend fun checkoutItem(itemId: String, quantity: Double, reason: String = "טיפול"): Resource<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_item_id", itemId)
                put("p_quantity", quantity)
                put("p_reason", reason)
            }
            postgrest.rpc("checkout_item", params)
            Resource.Success(Unit)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val hebrew = if (msg.contains("אין מספיק מלאי") || msg.contains("insufficient", true)) {
                "אין מספיק מלאי זמין"
            } else {
                mapErrorToHebrewMessage(e)
            }
            Resource.Error(hebrew, e)
        }
    }

    /** קליטת מלאי (type="in") או התאמת ספירה (type="adjustment") - מנהל/ת בלבד */
    suspend fun recordTransaction(transaction: NewTransaction): Resource<Unit> = try {
        postgrest.from("transactions").insert(transaction)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /** היסטוריית תנועות לפריט מסוים, מהחדש לישן - לכרטיס פריט (שלב 5) ולדוחות (שלב 6) */
    suspend fun getTransactionsForItem(itemId: String): Resource<List<InventoryTransaction>> = try {
        val rows = postgrest.from("transactions")
            .select { filter { eq("item_id", itemId) } }
            .decodeList<InventoryTransaction>()
            .sortedByDescending { it.performedAt }
        Resource.Success(rows)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }
}
