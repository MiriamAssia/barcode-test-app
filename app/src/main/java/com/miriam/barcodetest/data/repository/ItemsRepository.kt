package com.miriam.barcodetest.data.repository

import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.SupabaseClientProvider
import com.miriam.barcodetest.data.mapErrorToHebrewMessage
import com.miriam.barcodetest.data.model.Item
import com.miriam.barcodetest.data.model.ItemStockStatus
import com.miriam.barcodetest.data.model.NewItem
import io.github.jan.supabase.postgrest.postgrest

class ItemsRepository {

    private val postgrest = SupabaseClientProvider.client.postgrest

    /** כל הפריטים הפעילים, ממוינים לפי שם - למסך הוצאה מהירה / ניהול מלאי */
    suspend fun getActiveItems(): Resource<List<Item>> = try {
        val items = postgrest.from("items")
            .select { filter { eq("is_active", true) } }
            .decodeList<Item>()
            .sortedBy { it.name }
        Resource.Success(items)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /** סטטוס מלאי נוכחי לכל פריט (תקין/נמוך/אזל) - מבוסס על ה-view item_stock_status */
    suspend fun getStockStatuses(): Resource<List<ItemStockStatus>> = try {
        val rows = postgrest.from("item_stock_status")
            .select { filter { eq("is_active", true) } }
            .decodeList<ItemStockStatus>()
            .sortedBy { it.name }
        Resource.Success(rows)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /** הוספת פריט חדש - מנהל/ת בלבד (נאכף ב-RLS, לא רק בקוד) */
    suspend fun addItem(newItem: NewItem): Resource<Item> = try {
        val created = postgrest.from("items")
            .insert(newItem) { select() }
            .decodeSingle<Item>()
        Resource.Success(created)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }
}
