package com.miriam.barcodetest

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.repository.AuthRepository
import com.miriam.barcodetest.databinding.ActivityLoginBinding
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

/**
 * מסך התחברות אישי (אימייל+סיסמה). מחליף את מסך "התחברות ל-Supabase" הישן -
 * ה-URL והמפתח של הפרויקט קבועים עכשיו (BuildConfig), וכל איש/אשת צוות
 * מתחבר/ת עם המשתמש/ת האישי/ת שלו/ה כדי ש-RLS ידע להבחין בין צוות למנהל/ת.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener { attemptLogin() }

        // אם כבר יש session שמור מפעם קודמת - עוברים ישר למסך הראשי בלי
        // להציג את טופס ההתחברות בכלל.
        lifecycleScope.launch {
            authRepository.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> goToMain()
                    is SessionStatus.Initializing -> setLoading(true)
                    is SessionStatus.NotAuthenticated -> setLoading(false)
                    is SessionStatus.RefreshFailure -> setLoading(false)
                }
            }
        }
    }

    private fun attemptLogin() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        if (email.isBlank() || password.isBlank()) {
            binding.errorText.text = "יש למלא אימייל וסיסמה"
            return
        }

        binding.errorText.text = ""
        setLoading(true)

        lifecycleScope.launch {
            val result = authRepository.signIn(email, password)
            if (result is Resource.Error) {
                setLoading(false)
                binding.errorText.text = result.message
            }
            // בהצלחה - המעבר למסך הראשי קורה אוטומטית דרך המעקב אחרי sessionStatus למעלה
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
