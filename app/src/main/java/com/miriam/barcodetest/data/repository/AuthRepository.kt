package com.miriam.barcodetest.data.repository

import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.SupabaseClientProvider
import com.miriam.barcodetest.data.mapErrorToHebrewMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * התחברות/התנתקות אישית וניהול session, מעל מודול ה-Auth של Supabase.
 * ה-SDK דואג לבד לרענון טוקן אוטומטי ולשמירת ה-session בין הפעלות של האפליקציה.
 */
class AuthRepository {

    private val auth = SupabaseClientProvider.client.auth

    /** מצב ההתחברות הנוכחי - מתעדכן אוטומטית. LoginActivity/MainActivity עוקבים אחריו. */
    val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    suspend fun signIn(email: String, password: String): Resource<Unit> {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(mapSignInError(e), e)
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (_: Exception) {
            // גם אם אין רשת כרגע - עדיף לנקות session מקומי ולא לתקוע
            // את המשתמש/ת במסך הראשי בלי אפשרות להתנתק
        }
    }

    fun currentUserEmail(): String? = auth.currentUserOrNull()?.email

    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    private fun mapSignInError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("Invalid login credentials", true) -> "אימייל או סיסמה שגויים"
            msg.contains("Email not confirmed", true) -> "יש לאשר את כתובת האימייל לפני ההתחברות"
            else -> mapErrorToHebrewMessage(e)
        }
    }
}
