package com.miriam.barcodetest.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// מודלים תואמים לסכמת ה-DB משלב 1 (supabase/schema.sql)
// ============================================================================

// ---------- items ----------

@Serializable
data class Item(
    val id: String,
    val name: String,
    val category: String? = null,
    val unit: String = "יחידה",
    @SerialName("min_quantity") val minQuantity: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)

/** להוספת פריט חדש - בלי id (ה-DB מייצר אותו). מנהל/ת בלבד (RLS). */
@Serializable
data class NewItem(
    val name: String,
    val category: String? = null,
    val unit: String = "יחידה",
    @SerialName("min_quantity") val minQuantity: Double = 0.0
)

// ---------- batches ----------

@Serializable
data class Batch(
    val id: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("batch_number") val batchNumber: String? = null,
    val quantity: Double = 0.0,
    @SerialName("expiry_date") val expiryDate: String? = null,
    val supplier: String? = null,
    @SerialName("received_date") val receivedDate: String? = null
)

/**
 * להוספת אצווה חדשה - הכמות ההתחלתית לא נכנסת כאן. היא תמיד מתחילה מ-0
 * ומתעדכנת רק דרך תנועת 'in' (ראו TransactionsRepository.recordTransaction),
 * כדי שהכמות תמיד תהיה נגזרת מהיומן ולא תיכתב ישירות.
 */
@Serializable
data class NewBatch(
    @SerialName("item_id") val itemId: String,
    @SerialName("batch_number") val batchNumber: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    val supplier: String? = null
)

// ---------- transactions ----------

@Serializable
data class InventoryTransaction(
    val id: String,
    val type: String, // "in" | "out" | "adjustment"
    @SerialName("item_id") val itemId: String,
    @SerialName("batch_id") val batchId: String? = null,
    val quantity: Double,
    val reason: String? = null,
    val notes: String? = null,
    @SerialName("performed_by") val performedBy: String? = null,
    @SerialName("performed_at") val performedAt: String? = null
)

/**
 * להוספת תנועת 'in' או 'adjustment' ישירות - מנהל/ת בלבד (נאכף ב-RLS).
 * הוצאת מלאי ('out') לא עוברת דרך זה בכלל - ראו checkoutItem ב-TransactionsRepository.
 */
@Serializable
data class NewTransaction(
    val type: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("batch_id") val batchId: String? = null,
    val quantity: Double,
    val reason: String? = null,
    val notes: String? = null
)

// ---------- profiles ----------

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val role: String = "staff"
) {
    val isManager: Boolean get() = role == "manager"
}

// ---------- views (item_stock_status, expiring_batches מ-schema.sql) ----------

@Serializable
data class ItemStockStatus(
    val id: String,
    val name: String,
    val category: String? = null,
    val unit: String,
    @SerialName("min_quantity") val minQuantity: Double,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("current_quantity") val currentQuantity: Double,
    val status: String // "ok" | "low" | "out"
)

@Serializable
data class ExpiringBatch(
    @SerialName("batch_id") val batchId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("batch_number") val batchNumber: String? = null,
    val quantity: Double,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("days_until_expiry") val daysUntilExpiry: Int? = null
)
