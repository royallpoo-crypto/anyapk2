package com.anyapk.installer

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class InstallActivity : AppCompatActivity() {

    private lateinit var apkUri: Uri
    private lateinit var infoText: TextView
    private lateinit var installButton: Button
    
    // Flag to track if this is YagniLauncher
    private var isYagniLauncher = false
    private var yagniLauncherInfo: Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_install)

        infoText = findViewById(R.id.infoText)
        installButton = findViewById(R.id.installButton)

        // Get APK from intent
        apkUri = intent.data ?: run {
            Toast.makeText(this, getString(R.string.error_no_apk), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val fileName = apkUri.lastPathSegment ?: "Unknown APK"
        
        // Check if this is YagniLauncher
        isYagniLauncher = fileName == "YagniLauncher-v0.4.3-alpha.apk"
        
        if (isYagniLauncher) {
            // Get info about YagniLauncher from assets
            yagniLauncherInfo = AdbInstaller.getYagniLauncherInfo(this)
            infoText.text = """
                📱 YagniLauncher Detected!
                
                File: $fileName
                In Assets: ${yagniLauncherInfo?.get("inAssets")}
                Size: ${formatFileSize(yagniLauncherInfo?.get("assetSize") as? Int ?: 0)}
                
                Tap Install to proceed
            """.trimIndent()
        } else {
            infoText.text = getString(R.string.install_ready, fileName)
        }

        installButton.setOnClickListener {
            installApk()
        }
    }

    private fun installApk() {
        lifecycleScope.launch {
            try {
                installButton.isEnabled = false
                infoText.text = getString(R.string.installing)

                if (isYagniLauncher) {
                    // Use special YagniLauncher installation
                    installYagniLauncher()
                } else {
                    // Regular APK installation
                    installRegularApk()
                }

            } catch (e: Exception) {
                Toast.makeText(this@InstallActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                installButton.isEnabled = true
                e.printStackTrace()
            }
        }
    }

    private suspend fun installYagniLauncher() {
        // First check if we have the APK in assets
        val info = AdbInstaller.getYagniLauncherInfo(this)
        
        if (info["inAssets"] == false) {
            // Fall back to installing from URI if not in assets
            Toast.makeText(this, 
                "YagniLauncher not found in assets, installing from selected file...", 
                Toast.LENGTH_LONG).show()
            installRegularApk()
            return
        }

        // Check ADB connection
        val connectionStatus = AdbInstaller.getConnectionStatus(this)
        
        when (connectionStatus) {
            AdbInstaller.ConnectionStatus.CONNECTED -> {
                // Install YagniLauncher
                val result = AdbInstaller.installYagniLauncher(this)
                
                result.onSuccess { message ->
                    Toast.makeText(this, 
                        "✅ YagniLauncher installed successfully!", 
                        Toast.LENGTH_LONG).show()
                    showPostInstallOptions()
                }.onFailure { error ->
                    val errorMsg = error.message ?: "Unknown error"
                    infoText.text = "❌ Installation failed:\n$errorMsg"
                    Toast.makeText(this, 
                        "Installation failed: $errorMsg", 
                        Toast.LENGTH_LONG).show()
                    installButton.isEnabled = true
                }
            }
            AdbInstaller.ConnectionStatus.NEEDS_PAIRING -> {
                infoText.text = """
                    ⚠️ Device not paired
                    
                    Please pair your device first:
                    1. Enable Wireless Debugging in Developer Options
                    2. Open anyapk and pair your device
                    3. Try again
                    
                    Current status: Needs pairing
                """.trimIndent()
                installButton.isEnabled = true
            }
            else -> {
                infoText.text = """
                    ⚠️ Not connected to ADB
                    
                    Please enable Wireless Debugging:
                    Settings → Developer Options → Wireless Debugging
                    
                    Current status: Not connected
                """.trimIndent()
                installButton.isEnabled = true
            }
        }
    }

    private suspend fun installRegularApk() {
        try {
            // Copy APK to accessible location
            val tempFile = File(cacheDir, "temp_install_${System.currentTimeMillis()}.apk")
            contentResolver.openInputStream(apkUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Install using ADB
            val result = AdbInstaller.install(this@InstallActivity, tempFile.absolutePath)

            result.onSuccess { message ->
                Toast.makeText(this@InstallActivity, 
                    getString(R.string.install_success), 
                    Toast.LENGTH_LONG).show()
                tempFile.delete()
                finish()
            }

            result.onFailure { error ->
                val errorMsg = error.message ?: "Unknown error"
                infoText.text = getString(R.string.install_failed, errorMsg)
                Toast.makeText(this@InstallActivity, 
                    getString(R.string.install_failed, errorMsg), 
                    Toast.LENGTH_LONG).show()
                installButton.isEnabled = true
                tempFile.delete()
            }

        } catch (e: Exception) {
            throw e
        }
    }

    private fun showPostInstallOptions() {
        // Show options after successful YagniLauncher installation
        infoText.text = """
            ✅ YagniLauncher installed successfully!
            
            What would you like to do?
            
            • Open YagniLauncher
            • Install another APK
            • Close
        """.trimIndent()
        
        installButton.text = "Open YagniLauncher"
        installButton.setOnClickListener {
            // Try to launch YagniLauncher
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.yagni.launcher")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    finish()
                } else {
                    Toast.makeText(this, "Could not find YagniLauncher to launch", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error launching app: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun formatFileSize(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    // Optional: Add a debug method to check assets
    private fun checkAssets() {
        try {
            val assets = assets.list("")
            assets?.forEach {
                if (it == "YagniLauncher-v0.4.3-alpha.apk") {
                    Toast.makeText(this, "✅ YagniLauncher found in assets!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
