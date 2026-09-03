package com.miriam.barcodetest.data.repository

import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.SupabaseClientProvider
import com.miriam.barcodetest.data.mapErrorToHebrewMessage
import com.miriam.barcodetest.data.model.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

class ProfileRepository {

    private val client = SupabaseClientProvider.client

    /** הפרופיל (כולל תפקיד staff/manager) של המשתמש/ת המחוברת כרגע */
    suspend fun getCurrentProfile(): Resource<Profile> {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("אין משתמש/ת מחוברת")
        return try {
            val profile = client.postgrest.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingle<Profile>()
            Resource.Success(profile)
        } catch (e: Exception) {
            Resource.Error(mapErrorToHebrewMessage(e), e)
        }
    }

    /**
     * שמות המשתמשים לתצוגה בהיסטוריית התנועות, ממופים לפי מזהה.
     *
     * שימו לב לאופן שבו RLS משפיע כאן (ראו schema.sql, שלב 1): מנהל/ת מקבל/ת
     * את כל הפרופילים, ואיש/אשת צוות מקבל/ת רק את הפרופיל של עצמו/ה. לכן
     * לצוות קליני יופיעו שמות רק לפעולות שהם עצמם ביצעו - זו התנהגות מכוונת
     * ולא תקלה. מזהה שלא נמצא במפה יוצג כ"משתמש/ת לא ידוע/ה".
     */
    suspend fun getProfileNames(): Resource<Map<String, String>> = try {
        val profiles = client.postgrest.from("profiles")
            .select()
            .decodeList<Profile>()
        val names = profiles.associate { profile ->
            profile.id to (profile.fullName?.takeIf { it.isNotBlank() } ?: profile.id)
        }
        Resource.Success(names)
    } catch (e: Exception) {
        Resource.Error(mapErrorToHebrewMessage(e), e)
    }
}
