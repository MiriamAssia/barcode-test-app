package com.miriam.barcodetest

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.miriam.barcodetest.databinding.ActivityMainBinding
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private val client = OkHttpClient()

    // Table name in Supabase — matches the "Test" table shown in the Supabase Table Editor
    private val TABLE_NAME = "Test"

    private var supaUrl: String = ""
    private var supaKey: String = ""

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            binding.resultText.text = "הסריקה בוטלה"
        } else {
            binding.resultText.text = "נסרק: ${result.contents}\nשולח לשרת..."
            insertScannedRow(result.contents)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchScanner()
        } else {
            binding.resultText.text = "צריך הרשאת מצלמה כדי לסרוק"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("supabase_prefs", Context.MODE_PRIVATE)

        val savedUrl = prefs.getString("supa_url", "") ?: ""
        val savedKey = prefs.getString("supa_key", "") ?: ""

        if (savedUrl.isNotBlank() && savedKey.isNotBlank()) {
            supaUrl = savedUrl
            supaKey = savedKey
            showMainScreen()
        } else {
            showConnectScreen()
        }

        binding.connectButton.setOnClickListener {
            val urlInput = binding.supaUrlInput.text.toString().trim().trimEnd('/')
            val keyInput = binding.supaKeyInput.text.toString().trim()
            if (urlInput.isBlank() || keyInput.isBlank()) {
                binding.connectStatusText.text = "יש למלא גם Project URL וגם מפתח API"
                return@setOnClickListener
            }
            testConnection(urlInput, keyInput)
        }

        binding.scanButton.setOnClickListener {
            checkPermissionAndScan()
        }

        binding.changeConnectionButton.setOnClickListener {
            showConnectScreen()
        }
    }

    private fun showConnectScreen() {
        binding.connectLayout.visibility = android.view.View.VISIBLE
        binding.mainLayout.visibility = android.view.View.GONE
    }

    private fun showMainScreen() {
        binding.connectLayout.visibility = android.view.View.GONE
        binding.mainLayout.visibility = android.view.View.VISIBLE
    }

    private fun testConnection(url: String, key: String) {
        binding.connectButton.isEnabled = false
        binding.connectButton.text = "מתחבר..."
        binding.connectStatusText.text = ""

        val request = Request.Builder()
            .url("$url/rest/v1/$TABLE_NAME?select=id&limit=1")
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    binding.connectStatusText.text = "לא הצלחתי להגיע לכתובת. בדקי את ה-URL ואת החיבור לאינטרנט."
                    binding.connectButton.isEnabled = true
                    binding.connectButton.text = "התחבר"
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        supaUrl = url
                        supaKey = key
                        prefs.edit()
                            .putString("supa_url", url)
                            .putString("supa_key", key)
                            .apply()
                        showMainScreen()
                    } else {
                        val body = response.body?.string() ?: ""
                        binding.connectStatusText.text = "שגיאה (${response.code}): טבלה '$TABLE_NAME' לא נמצאה, או שגיאת הרשאה. $body"
                    }
                    binding.connectButton.isEnabled = true
                    binding.connectButton.text = "התחבר"
                }
            }
        })
    }

    private fun checkPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchScanner()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
        options.setPrompt("כוון את המצלמה לברקוד")
        options.setBeepEnabled(true)
        options.setOrientationLocked(true)
        barcodeLauncher.launch(options)
    }

    private fun insertScannedRow(scannedValue: String) {
        val json = JSONObject()
        json.put("name", scannedValue)

        val mediaType = "application/json".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$supaUrl/rest/v1/$TABLE_NAME")
            .addHeader("apikey", supaKey)
            .addHeader("Authorization", "Bearer $supaKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    binding.resultText.text = "נסרק: $scannedValue\nשגיאה: לא הצלחתי להגיע לשרת"
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        binding.resultText.text = "נסרק: $scannedValue\n✓ נשמר בהצלחה בטבלה"
                    } else {
                        val errBody = response.body?.string() ?: ""
                        binding.resultText.text = "נסרק: $scannedValue\nשגיאה (${response.code}): $errBody"
                    }
                }
            }
        })
    }
}
