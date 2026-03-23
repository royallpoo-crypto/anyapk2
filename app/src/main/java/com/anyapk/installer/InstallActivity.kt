package com.anyapk.installer

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class InstallActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var installButton: Button
    private lateinit var cancelButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_install)

        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        installButton = findViewById(R.id.installButton)
        cancelButton = findViewById(R.id.cancelButton)

        val apkPath = intent.getStringExtra("apkPath")

        if (apkPath != null) {
            handleInstall(apkPath)
        } else {
            showError("No APK file provided")
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun handleInstall(apkPath: String) {
        statusText.text = "Preparing to install APK..."
        progressBar.isIndeterminate = true
        installButton.isEnabled = false
        cancelButton.isEnabled = false

        lifecycleScope.launch {
            val result = AdbInstaller.install(this@InstallActivity, apkPath)

            result.onSuccess { message ->
                statusText.text = "✅ $message"
                progressBar.isIndeterminate = false
                progressBar.progress = 100
                Toast.makeText(
                    this@InstallActivity,
                    "APK installed successfully!",
                    Toast.LENGTH_LONG
                ).show()
                installButton.text = "Done"
                installButton.isEnabled = true
                installButton.setOnClickListener {
                    finish()
                }
            }

            result.onFailure { error ->
                showError("Installation failed: ${error.message}")
                progressBar.isIndeterminate = false
                installButton.text = "Try Again"
                installButton.isEnabled = true
                installButton.setOnClickListener {
                    handleInstall(apkPath)
                }
            }

            // Clean up temp file if it was created
            try {
                val tempFile = File(cacheDir, "dummy_install.apk")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    private fun showError(message: String) {
        statusText.text = "❌ $message"
        progressBar.isIndeterminate = false
        installButton.isEnabled = true
        cancelButton.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
