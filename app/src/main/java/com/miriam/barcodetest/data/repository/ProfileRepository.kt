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
}
