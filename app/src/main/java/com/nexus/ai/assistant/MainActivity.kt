package com.nexus.ai.assistant

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.nexus.ai.assistant.core.AndroidApprovedApkInstaller
import com.nexus.ai.assistant.core.CodePatch
import com.nexus.ai.assistant.core.GovernanceManager
import com.nexus.ai.assistant.core.HybridAIEngine
import com.nexus.ai.assistant.core.PreReleaseQualityGate
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var governance: GovernanceManager
    private lateinit var aiEngine: HybridAIEngine
    private lateinit var qualityGate: PreReleaseQualityGate
    private lateinit var apkInstaller: AndroidApprovedApkInstaller

    private var activePatch: CodePatch? = null

    private val apkPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val tvApkStatus = findViewById<TextView>(R.id.tvApkStatus)
            val consent = findViewById<CheckBox>(R.id.cbApkUserConsent).isChecked
            if (uri == null) {
                tvApkStatus.text = "No APK file selected."
                return@registerForActivityResult
            }
            try {
                val msg = apkInstaller.installApkWithUserConsent(uri, consent)
                tvApkStatus.text = msg
            } catch (e: Exception) {
                tvApkStatus.text = "GOVERNANCE BLOCK: ${e.message}"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        governance = GovernanceManager(this)
        aiEngine = HybridAIEngine(governance)
        qualityGate = PreReleaseQualityGate()
        apkInstaller = AndroidApprovedApkInstaller(this)

        val tvHeader = findViewById<TextView>(R.id.tvHeaderTitle)
        tvHeader.text = "Kiko AI v${governance.getCurrentVersion()}"

        setupTabs()
        setupChatTab()
        setupCodeAndReleaseTab(tvHeader)
        setupApkTab()
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val pChat = findViewById<LinearLayout>(R.id.panelChat)
        val pCode = findViewById<LinearLayout>(R.id.panelCode)
        val pHistory = findViewById<LinearLayout>(R.id.panelHistory)
        val pApk = findViewById<LinearLayout>(R.id.panelApk)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                pChat.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                pCode.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                pHistory.visibility = if (tab.position == 2) View.VISIBLE else View.GONE
                pApk.visibility = if (tab.position == 3) View.VISIBLE else View.GONE
                if (tab.position == 2) refreshHistoryView()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupChatTab() {
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val cbOffline = findViewById<CheckBox>(R.id.cbForceOffline)
        val tvLog = findViewById<TextView>(R.id.tvChatLog)
        val etPrompt = findViewById<EditText>(R.id.etPrompt)
        val btnSend = findViewById<Button>(R.id.btnSendPrompt)

        btnSend.setOnClickListener {
            val prompt = etPrompt.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener
            etPrompt.text.clear()
            tvLog.append("\nUSER: $prompt\n")

            lifecycleScope.launch {
                val res = aiEngine.processPrompt(prompt, etApiKey.text.toString(), cbOffline.isChecked)
                tvLog.append("[${res.source}]: ${res.text}\n")
            }
        }
    }

    private fun setupCodeAndReleaseTab(tvHeader: TextView) {
        val etInstruction = findViewById<EditText>(R.id.etInstruction)
        val etModule = findViewById<EditText>(R.id.etTargetModule)
        val etCode = findViewById<EditText>(R.id.etProposedCode)
        val btnGates = findViewById<Button>(R.id.btnRunGates)
        val tvReport = findViewById<TextView>(R.id.tvGateReport)
        val cbReview = findViewById<CheckBox>(R.id.cbCodeReviewDone)
        val cbApprove = findViewById<CheckBox>(R.id.cbFeatureApproved)
        val btnRelease = findViewById<Button>(R.id.btnReleaseVersion)

        btnGates.setOnClickListener {
            try {
                val patch = qualityGate.draftPatch(
                    etInstruction.text.toString(),
                    etModule.text.toString(),
                    etCode.text.toString()
                )
                val report = qualityGate.validatePatch(patch)
                activePatch = if (report.readyForApproval) patch else null
                btnRelease.isEnabled = report.readyForApproval

                tvReport.text = "${report.errorCheckMessage}\n${report.securityTestMessage}\n\n=== CODE REVIEW SUMMARY ===\n${report.codeReviewSummary}"
            } catch (e: Exception) {
                activePatch = null
                btnRelease.isEnabled = false
                tvReport.text = "REJECTED: ${e.message}"
            }
        }

        btnRelease.setOnClickListener {
            val patch = activePatch ?: return@setOnClickListener
            try {
                val record = governance.approveAndRelease(
                    patch = patch,
                    reviewConfirmed = cbReview.isChecked,
                    featureApproved = cbApprove.isChecked
                )
                tvHeader.text = "Kiko AI v${record.version}"
                btnRelease.isEnabled = false
                activePatch = null
                Toast.makeText(this, "Backup created & v${record.version} released!", Toast.LENGTH_LONG).show()
                tvReport.append("\n\nRELEASED v${record.version}\nBackup: ${record.backupPath}")
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshHistoryView() {
        val tvHistory = findViewById<TextView>(R.id.tvHistoryList)
        val list = governance.getVersionHistory()
        tvHistory.text = list.joinToString("\n------------------------\n") {
            "Version: v${it.version} (from v${it.previousVersion})\n" +
            "Time: ${it.timestamp}\n" +
            "Instruction: ${it.instruction}\n" +
            "Module: ${it.targetModule}\n" +
            "Backup: ${it.backupPath}"
        }
    }

    private fun setupApkTab() {
        val cbConsent = findViewById<CheckBox>(R.id.cbApkUserConsent)
        val btnSelect = findViewById<Button>(R.id.btnSelectAndInstallApk)
        val tvStatus = findViewById<TextView>(R.id.tvApkStatus)

        btnSelect.setOnClickListener {
            if (!cbConsent.isChecked) {
                tvStatus.text = "USER CONTROL BLOCK: Please check the User Consent box first."
                return@setOnClickListener
            }
            apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive"))
        }
    }
}
