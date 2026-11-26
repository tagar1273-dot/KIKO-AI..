package com.nexus.ai.assistant.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AIResponse(val source: String, val text: String)

class HybridAIEngine(private val governance: GovernanceManager) {

    suspend fun processPrompt(
        prompt: String,
        apiKey: String,
        forceOffline: Boolean
    ): AIResponse = withContext(Dispatchers.IO) {
        val cmd = prompt.trim().lowercase(Locale.ROOT)

        when (cmd) {
            "status" -> return@withContext AIResponse(
                "OFFLINE_ENGINE",
                "Kiko AI v${governance.getCurrentVersion()} | Mode: ${if (forceOffline) "OFFLINE" else "HYBRID"} | User Control: ENFORCED"
            )
            "time" -> {
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                return@withContext AIResponse("OFFLINE_ENGINE", "Local System Time: $now")
            }
            "version" -> return@withContext AIResponse(
                "OFFLINE_ENGINE",
                "Active Approved Version: v${governance.getCurrentVersion()}"
            )
            "help" -> return@withContext AIResponse(
                "OFFLINE_ENGINE",
                "Offline Commands: status, time, version, help | Enter Cloud API Key for Generative AI."
            )
        }

        if (forceOffline || apiKey.isBlank()) {
            return@withContext AIResponse(
                "OFFLINE_FALLBACK",
                "[Offline Mode] Command '$prompt' processed locally. Connect Cloud API key for full LLM intelligence."
            )
        }

        try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 15000
            }

            val payload = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "You are Kiko AI. Strictly obey user control."))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(raw)
                val reply = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                AIResponse("CLOUD_AI", reply.trim())
            } else {
                AIResponse("OFFLINE_FAILOVER", "Cloud HTTP ${conn.responseCode}. Switched safely to Offline Engine.")
            }
        } catch (e: Exception) {
            AIResponse("OFFLINE_FAILOVER", "Cloud unreachable (${e.message}). Running on Offline Engine.")
        }
    }
}
