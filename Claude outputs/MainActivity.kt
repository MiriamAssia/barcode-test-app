package com.miriam.barcodetest

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miriam.barcodetest.data.repository.AuthRepository
import com.miriam.barcodetest.databinding.ActivityMainBinding
import com.miriam.barcodetest.ui.CheckoutFragment
import com.miriam.barcodetest.ui.IntakeFragment
import com.miriam.barcodetest.ui.InventoryFragment
import com.miriam.barcodetest.ui.PlaceholderFragment
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

/**
 * המסך המארח היחיד של האפליקציה.
 *
 * למה Activity אחד ולא Activity לכל מסך: בעיצוב סרגל הניווט התחתון קבוע
 * ורק אזור התוכן מתחלף. עם Activities נפרדים הסרגל היה נבנה מחדש בכל מעבר
 * (הבהוב), וכל טאב היה טוען מחדש נתונים שכבר נטענו. כאן הסרגל מצויר פעם
 * אחת, והמעבר בין טאבים הוא החלפת Fragment בלבד - מיידי.
 *
 * מסך ההתחברות (LoginActivity) נשאר Activity נפרד כי אין לו סרגל ניווט.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authRepository = AuthRepository()

    /** מונע בנייה מחדש של אותו מסך כשלוחצים שוב על טאב שכבר פתוח */
    private var currentTabId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.settingsButton.setOnClickListener { showSettingsDialog() }

        binding.bottomNav.setOnItemSelectedListener { menuItem ->
            showTab(menuItem.itemId)
            true
        }

        if (savedInstanceState == null) {
            // מסך ההוצאה המהירה הוא ברירת המחדל - זה מה שהצוות הקליני צריך רוב הזמן
            binding.bottomNav.selectedItemId = R.id.nav_checkout
        } else {
            currentTabId = savedInstanceState.getInt(STATE_TAB_ID, 0)
        }

        // אם ה-session נופל (טוקן שפג ולא הצליח להתחדש) - חוזרים למסך התחברות
        lifecycleScope.launch {
            authRepository.sessionStatus.collect { status ->
                if (status is SessionStatus.NotAuthenticated) {
                    goToLogin()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB_ID, currentTabId)
    }

    private fun showTab(tabId: Int) {
        if (tabId == currentTabId) return
        currentTabId = tabId

        // הכותרת העליונה קבועה בכל המסכים - הסרגל התחתון כבר אומר איפה אנחנו
        val fragment: Fragment = when (tabId) {
            R.id.nav_checkout -> CheckoutFragment()
            R.id.nav_intake -> IntakeFragment()
            R.id.nav_inventory -> InventoryFragment()
            R.id.nav_alerts -> PlaceholderFragment.newInstance(
                getString(R.string.nav_alerts), R.drawable.ic_notifications
            )
            else -> PlaceholderFragment.newInstance(
                getString(R.string.nav_reports), R.drawable.ic_analytics
            )
        }

        // מסך משנה (למשל כרטיס פריט) יכול להיות פתוח מעל הטאב הקודם. בלי
        // לנקות אותו, לחיצה על "חזרה" אחרי מעבר טאב הייתה מחזירה למסך של
        // הטאב הקודם - התנהגות מבלבלת.
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commit()
    }

    /**
     * תפריט הגדרות זמני: מציג מי מחובר/ת ומאפשר התנתקות. מסך הגדרות מלא
     * (ניהול פריטים והרשאות) ייבנה בשלב נפרד.
     */
    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setMessage(authRepository.currentUserEmail() ?: "")
            .setPositiveButton(R.string.logout) { _, _ -> logout() }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    private companion object {
        const val STATE_TAB_ID = "current_tab_id"
    }
}
