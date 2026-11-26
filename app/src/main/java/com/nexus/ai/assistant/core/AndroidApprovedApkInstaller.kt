package com.nexus.ai.assistant.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.Toast

class ApkInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmIntent != null) {
                    context.startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Kiko AI: Approved APK Installed Successfully!", Toast.LENGTH_LONG).show()
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Cancelled or Failed"
                Toast.makeText(context, "APK Install Status: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class AndroidApprovedApkInstaller(private val context: Context) {

    fun installApkWithUserConsent(apkUri: Uri, userConsentGiven: Boolean): String {
        if (!userConsentGiven) {
            throw SecurityException("BLOCKED BY USER CONTROL: Explicit user consent is required before APK installation.")
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)

        packageInstaller.openSession(sessionId).use { session ->
            context.contentResolver.openInputStream(apkUri)?.use { input ->
                session.openWrite("kiko_update_package", 0, -1).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            } ?: throw IllegalArgumentException("Unable to read selected APK file.")

            val intent = Intent(context, ApkInstallStatusReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pendingIntent.intentSender)
        }

        return "Android PackageInstaller Session #$sessionId committed. Awaiting system confirmation prompt."
    }
}
