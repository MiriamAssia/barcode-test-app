package com.miriam.barcodetest

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.repository.AuthRepository
import com.miriam.barcodetest.data.repository.ItemsRepository
import com.miriam.barcodetest.data.repository.ProfileRepository
import com.miriam.barcodetest.databinding.ActivityMainBinding
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

/**
 * מסך בית זמני לשלב 2: מוודא שההתחברות ושכבת הנתונים עובדות מקצה לקצה
 * (מציג תפקיד + מספר פריטים פעילים מהמסד). מסכי העבודה האמיתיים (הוצאת
 * מלאי, קליטה, ניהול וכו') יחליפו את זה בשלבים הבאים.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authRepository = AuthRepository()
    private val profileRepository = ProfileRepository()
    private val itemsRepository = ItemsRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.userEmailText.text = authRepository.currentUserEmail() ?: ""
        binding.logoutButton.setOnClickListener { logout() }

        loadProfile()
        loadItemsStatus()

        // אם ה-session נופל (למשל טוקן שפג ולא הצליח להתחדש) - חוזרים למסך התחברות
        lifecycleScope.launch {
            authRepository.sessionStatus.collect { status ->
                if (status is SessionStatus.NotAuthenticated) {
                    goToLogin()
                }
            }
        }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            when (val result = profileRepository.getCurrentProfile()) {
                is Resource.Success -> {
                    val roleLabel = if (result.data.isManager) "מנהל/ת מלאי" else "צוות קליני"
                    binding.roleText.text = "תפקיד: $roleLabel"
                }
                is Resource.Error -> binding.roleText.text = "תפקיד: לא ידוע (${result.message})"
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadItemsStatus() {
        binding.itemsStatusText.text = "טוען פריטים..."
        lifecycleScope.launch {
            when (val result = itemsRepository.getActiveItems()) {
                is Resource.Success -> {
                    binding.itemsStatusText.text =
                        "החיבור לבסיס הנתונים עובד ✓\nמספר פריטים פעילים: ${result.data.size}"
                }
                is Resource.Error -> {
                    binding.itemsStatusText.text = "שגיאה בטעינת פריטים: ${result.message}"
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            authRepository.signOut()
            goToLogin()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
