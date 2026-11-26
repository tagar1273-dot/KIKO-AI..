package com.nexus.ai.assistant.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VersionRecord(
    val version: String,
    val previousVersion: String,
    val timestamp: String,
    val patchId: String,
    val instruction: String,
    val targetModule: String,
    val backupPath: String
)

class GovernanceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("kiko_governance", Context.MODE_PRIVATE)
    private val workspaceDir = File(context.filesDir, "kiko_workspace").apply { mkdirs() }
    private val backupsDir = File(context.filesDir, "kiko_backups").apply { mkdirs() }
    private val historyFile = File(context.filesDir, "version_history.json")

    init {
        if (!prefs.contains("current_version")) {
            prefs.edit().putString("current_version", "1.0.0").apply()
        }
        if (!historyFile.exists()) {
            val initArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("version", "1.0.0")
                    put("previousVersion", "0.0.0")
                    put("timestamp", nowStr())
                    put("patchId", "BASE-INIT")
                    put("instruction", "Initial Base Android System")
                    put("targetModule", "MainActivity.kt")
                    put("backupPath", "N/A")
                })
            }
            historyFile.writeText(initArr.toString(2))
        }
    }

    private fun nowStr(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    fun getCurrentVersion(): String = prefs.getString("current_version", "1.0.0") ?: "1.0.0"

    fun createBackupBeforeUpdate(currentVersion: String): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val snapDir = File(backupsDir, "backup_v${currentVersion}_$ts").apply { mkdirs() }
        workspaceDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.copyTo(File(snapDir, file.name), overwrite = true)
            }
        }
        File(snapDir, "version_history_snapshot.json").writeText(historyFile.readText())
        return snapDir
    }

    private fun incrementVersion(current: String): String {
        val parts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 1 }
        val minor = parts.getOrElse(1) { 0 } + 1
        return "$major.$minor.0"
    }

    fun approveAndRelease(
        patch: CodePatch,
        reviewConfirmed: Boolean,
        featureApproved: Boolean
    ): VersionRecord {
        if (!reviewConfirmed) {
            throw SecurityException("BLOCKED: Code Review confirmation is mandatory before release.")
        }
        if (!featureApproved) {
            throw SecurityException("BLOCKED: New Feature Approval from User is required.")
        }

        val oldVersion = getCurrentVersion()
        val backupFolder = createBackupBeforeUpdate(oldVersion)

        val safeFileName = patch.targetModule.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        File(workspaceDir, safeFileName).writeText(patch.proposedCode)

        val newVersion = incrementVersion(oldVersion)
        prefs.edit().putString("current_version", newVersion).apply()

        val record = VersionRecord(
            version = newVersion,
            previousVersion = oldVersion,
            timestamp = nowStr(),
            patchId = patch.patchId,
            instruction = patch.userInstruction,
            targetModule = safeFileName,
            backupPath = backupFolder.absolutePath
        )

        val arr = JSONArray(historyFile.readText())
        val obj = JSONObject().apply {
            put("version", record.version)
            put("previousVersion", record.previousVersion)
            put("timestamp", record.timestamp)
            put("patchId", record.patchId)
            put("instruction", record.instruction)
            put("targetModule", record.targetModule)
            put("backupPath", record.backupPath)
        }
        val updated = JSONArray().apply {
            put(obj)
            for (i in 0 until arr.length()) put(arr.getJSONObject(i))
        }
        historyFile.writeText(updated.toString(2))

        return record
    }

    fun getVersionHistory(): List<VersionRecord> {
        val arr = JSONArray(historyFile.readText())
        val list = mutableListOf<VersionRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                VersionRecord(
                    version = o.getString("version"),
                    previousVersion = o.getString("previousVersion"),
                    timestamp = o.getString("timestamp"),
                    patchId = o.getString("patchId"),
                    instruction = o.getString("instruction"),
                    targetModule = o.getString("targetModule"),
                    backupPath = o.getString("backupPath")
                )
            )
        }
        return list
    }
}
