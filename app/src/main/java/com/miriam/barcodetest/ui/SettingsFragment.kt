package com.miriam.barcodetest.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.miriam.barcodetest.BuildConfig
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.AppSettings
import com.miriam.barcodetest.data.repository.AuthRepository
import com.miriam.barcodetest.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

/**
 * מסך הגדרות: סף התראת התפוגה, המשתמש/ת המחוברת וגרסת האפליקציה.
 *
 * מחליף את הדיאלוג הזמני שהיה ב-MainActivity. נפתח מעל הטאב הפעיל עם
 * addToBackStack, בדיוק כמו כרטיס הפריט, ולכן הסרגל התחתון נשאר גלוי
 * והחזרה ממנו מחזירה למקום שממנו נכנסו.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val authRepository = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.settingsBackButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.expiryDaysInput.setText(
            AppSettings.expiryWarningDays(requireContext()).toString()
        )
        binding.expiryMinus.setOnClickListener { changeDays(-1) }
        binding.expiryPlus.setOnClickListener { changeDays(1) }
        binding.saveExpiryButton.setOnClickListener { saveDays() }

        binding.settingsUserEmail.text =
            authRepository.currentUserEmail() ?: getString(R.string.settings_no_user)
        binding.settingsLogoutButton.setOnClickListener { logout() }

        binding.settingsVersion.text = getString(
            R.string.settings_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ======================= סף התראת תפוגה =======================

    private fun currentDays(): Long =
        binding.expiryDaysInput.text.toString().toLongOrNull()
            ?: AppSettings.DEFAULT_EXPIRY_WARNING_DAYS

    private fun changeDays(delta: Long) {
        val next = (currentDays() + delta).coerceIn(
            AppSettings.MIN_EXPIRY_WARNING_DAYS,
            AppSettings.MAX_EXPIRY_WARNING_DAYS
        )
        binding.expiryDaysInput.setText(next.toString())
        binding.expiryDaysInput.setSelection(binding.expiryDaysInput.text.length)
    }

    private fun saveDays() {
        val typed = binding.expiryDaysInput.text.toString().toLongOrNull()
        if (typed == null ||
            typed < AppSettings.MIN_EXPIRY_WARNING_DAYS ||
            typed > AppSettings.MAX_EXPIRY_WARNING_DAYS
        ) {
            toast(
                getString(
                    R.string.settings_expiry_invalid,
                    AppSettings.MIN_EXPIRY_WARNING_DAYS,
                    AppSettings.MAX_EXPIRY_WARNING_DAYS
                )
            )
            return
        }

        AppSettings.setExpiryWarningDays(requireContext(), typed)
        // מיישרים את השדה לערך שנשמר בפועל, כדי שלא יישאר על מספר שקוצץ
        binding.expiryDaysInput.setText(
            AppSettings.expiryWarningDays(requireContext()).toString()
        )
        toast(getString(R.string.settings_expiry_saved, typed))
    }

    // ======================= התנתקות =======================

    private fun logout() {
        lifecycleScope.launch {
            authRepository.signOut()
            // המעבר למסך ההתחברות קורה מעצמו: MainActivity עוקב אחרי
            // sessionStatus ומגיב ל-NotAuthenticated.
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
