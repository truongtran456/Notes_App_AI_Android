package com.philkes.notallyx.presentation.activity.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.ApiClient
import com.philkes.notallyx.data.api.saveServerUrl
import com.philkes.notallyx.data.preferences.AIUserPreferences
import com.philkes.notallyx.databinding.ActivityAiSettingsBinding
import java.util.UUID
import kotlinx.coroutines.launch

class AISettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSettingsBinding

    companion object {
        private const val PREFS_NAME = "ai_settings"
        private const val KEY_SERVER_URL = "server_url"

        fun start(context: Context) {
            context.startActivity(Intent(context, AISettingsActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadCurrentSettings()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadCurrentSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_SERVER_URL, ApiClient.ServerPresets.EMULATOR)
        binding.ServerUrlInput.setText(savedUrl)

        val currentUserId = AIUserPreferences.getOrCreateUserId(this)
        binding.UserIdInput.setText(currentUserId)
    }

    private fun setupClickListeners() {
        // Save button
        binding.SaveButton.setOnClickListener { saveSettings() }

        // Test connection button
        binding.TestConnectionButton.setOnClickListener { testConnection() }

        binding.GenerateUserIdButton.setOnClickListener {
            val newId = UUID.randomUUID().toString()
            binding.UserIdInput.setText(newId)
        }

        // Preset buttons
        binding.PresetEmulator.setOnClickListener {
            binding.ServerUrlInput.setText(ApiClient.ServerPresets.EMULATOR)
        }

        binding.PresetLocalhost.setOnClickListener {
            binding.ServerUrlInput.setText(ApiClient.ServerPresets.LOCALHOST)
        }

        binding.PresetLocalNetwork.setOnClickListener { showLocalNetworkDialog() }
    }

    private fun saveSettings() {
        val url = binding.ServerUrlInput.text.toString().trim()

        if (url.isBlank()) {
            Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure URL ends with /
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"

        // Save to SharedPreferences and update ApiClient
        saveServerUrl(normalizedUrl)

        val userId = binding.UserIdInput.text?.toString()?.trim().orEmpty()
        if (userId.isNotBlank()) {
            AIUserPreferences.setUserId(this, userId)
        } else {
            val generated = AIUserPreferences.getOrCreateUserId(this)
            binding.UserIdInput.setText(generated)
        }

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        val url = binding.ServerUrlInput.text.toString().trim()

        if (url.isBlank()) {
            Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            return
        }

        binding.TestConnectionButton.isEnabled = false
        binding.TestConnectionButton.text = "Testing..."

        // Temporarily set the URL
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        ApiClient.setBaseUrl(normalizedUrl)

        lifecycleScope.launch {
            val success = ApiClient.checkConnection()

            binding.TestConnectionButton.isEnabled = true
            binding.TestConnectionButton.text = getString(R.string.ai_test_connection)

            if (success) {
                Toast.makeText(
                        this@AISettingsActivity,
                        R.string.ai_connection_success,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } else {
                Toast.makeText(
                        this@AISettingsActivity,
                        R.string.ai_connection_failed,
                        Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }
    }

    private fun showLocalNetworkDialog() {
        val currentIp =
            binding.ServerUrlInput.text
                .toString()
                .replace("http://", "")
                .replace("https://", "")
                .replace(":8000/api/v1/", "")
                .takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) } ?: "192.168.1.100"

        val input =
            android.widget.EditText(this).apply {
                hint = "192.168.x.x"
                setText(currentIp)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }

        MaterialAlertDialogBuilder(this)
            .setTitle("Enter Local IP Address")
            .setMessage("Enter the IP address of your AI server on the local network")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotBlank()) {
                    binding.ServerUrlInput.setText("http://$ip:8000/api/v1/")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
