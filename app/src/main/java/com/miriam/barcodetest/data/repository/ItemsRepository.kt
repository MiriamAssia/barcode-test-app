package com.miriam.barcodetest.data.repository

import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.SupabaseClientProvider
import com.miriam.barcodetest.data.isDuplicateKey
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

    /**
     * סטטוס מלאי לכל הפריטים, כולל כאלה שכובו.
     *
     * נדרש למסך ההתראות: אצווה שעומדת לפוג יכולה להשתייך לפריט שכובה מאז,
     * וההתראה עליה עדיין מוצגת (המלאי הפיזי קיים וצריך לטפל בו). בלי הפריטים
     * הכבויים כאן, הקשה על התראה כזו לא הייתה פותחת כלום.
     */
    suspend fun getAllStockStatuses(): Resource<List<ItemStockStatus>> = try {
        val rows = postgrest.from("item_stock_status")
            .select()
            .decodeList<ItemStockStatus>()
            .sortedBy { it.name }
        Resource.Success(rows)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /**
     * הוספת פריט חדש - מנהל/ת בלבד (נאכף ב-RLS, לא רק בקוד).
     *
     * האינדקס הייחודי על barcode חל על כל הפריטים, גם על כבויים - ולכן ברקוד
     * של פריט שכובה ייכשל כאן, אחרי ש-findItemByBarcode (שמסננת לפעילים) לא
     * מצאה אותו. במקרה הזה מוחזרת הודעה שמסבירה בדיוק מה קרה, במקום שגיאת
     * מסד נתונים גולמית.
     */
    suspend fun addItem(newItem: NewItem): Resource<Item> = try {
        val created = postgrest.from("items")
            .insert(newItem) { select() }
            .decodeSingle<Item>()
        Resource.Success(created)
    } catch (e: Exception) {
        val message = if (newItem.barcode != null && isDuplicateKey(e)) {
            "הברקוד הזה כבר משויך לפריט אחר במערכת. ייתכן שהפריט כובה - " +
                "אפשר לחפש אותו לפי שם במסך המלאי ולהפעיל אותו מחדש."
        } else {
            mapErrorToHebrewMessage(e)
        }
        Resource.Error(message, e)
    }

    /**
     * חיפוש פריט לפי הברקוד שנסרק. מחזיר null אם אין פריט כזה - זה מצב תקין
     * ולא שגיאה (אז מסך הקליטה מציע ליצור פריט חדש עם הברקוד הזה).
     *
     * העמודה barcode נוספה ב-supabase/migration_02_barcode.sql.
     */
    suspend fun findItemByBarcode(barcode: String): Resource<Item?> = try {
        val matches = postgrest.from("items")
            .select {
                filter {
                    eq("barcode", barcode)
                    eq("is_active", true)
                }
            }
            .decodeList<Item>()
        Resource.Success(matches.firstOrNull())
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }

    /**
     * כל הפריטים, כולל כאלה שכובו (is_active = false).
     *
     * דוח היסטורי חייב לדעת לתרגם גם מזהה של פריט שהוצא משימוש מאז, אחרת
     * צריכה עבר שלו הייתה מופיעה בלי שם.
     */
    suspend fun getAllItems(): Resource<List<Item>> = try {
        val items = postgrest.from("items").select().decodeList<Item>()
        Resource.Success(items)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }
}
