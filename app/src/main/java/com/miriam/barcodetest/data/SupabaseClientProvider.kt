package com.miriam.barcodetest.data

import com.miriam.barcodetest.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * נקודת גישה יחידה ל-Supabase client. נבנה פעם אחת (lazy singleton) ומשמש
 * את כל שכבות ה-repository באפליקציה. ה-URL והמפתח קבועים לפרויקט (לא סוד,
 * מוגנים ע"י Row Level Security) ולא מוזנים יותר ע"י המשתמש/ת - ראו BuildConfig.
 */
object SupabaseClientProvider {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
