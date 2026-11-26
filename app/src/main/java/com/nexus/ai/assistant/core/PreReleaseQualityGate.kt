package com.nexus.ai.assistant.core

import java.security.MessageDigest

data class CodePatch(
    val patchId: String,
    val userInstruction: String,
    val targetModule: String,
    val proposedCode: String
)

data class ValidationReport(
    val errorCheckPassed: Boolean,
    val errorCheckMessage: String,
    val securityTestPassed: Boolean,
    val securityTestMessage: String,
    val codeReviewSummary: String,
    val readyForApproval: Boolean
)

class PreReleaseQualityGate {

    private val forbiddenPatterns = listOf(
        Regex("Runtime\\.getRuntime\\(\\)\\.exec") to "Unauthorized shell execution (Runtime.exec)",
        Regex("ProcessBuilder") to "Direct OS ProcessBuilder invocation",
        Regex("su\\s+-c") to "Root privilege escalation attempt",
        Regex("DexClassLoader") to "Dynamic unverified DEX loading",
        Regex("\\beval\\s*\\(") to "Dynamic eval() execution"
    )

    fun draftPatch(instruction: String, targetModule: String, code: String): CodePatch {
        if (instruction.trim().length < 4) {
            throw SecurityException("BLOCKED: Explicit user instruction is mandatory for code modification.")
        }
        if (code.isBlank()) {
            throw IllegalArgumentException("Code content cannot be empty.")
        }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$instruction|$targetModule|$code".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12)

        return CodePatch(hash, instruction.trim(), targetModule.trim(), code)
    }

    fun validatePatch(patch: CodePatch): ValidationReport {
        val openBraces = patch.proposedCode.count { it == '{' }
        val closeBraces = patch.proposedCode.count { it == '}' }
        val openParens = patch.proposedCode.count { it == '(' }
        val closeParens = patch.proposedCode.count { it == ')' }

        val errOk = (openBraces == closeBraces) && (openParens == closeParens)
        val errMsg = if (errOk) {
            "PASS: Syntax structure & delimiters balanced (0 errors)."
        } else {
            "FAIL (Error Check): Unbalanced brackets/parentheses ({:$openBraces }:$closeBraces (:$openParens ):$closeParens)."
        }

        var secOk = true
        var secMsg = "PASS: Security scan clean. No malicious or unauthorized OS vectors."
        for ((regex, reason) in forbiddenPatterns) {
            if (regex.containsMatchIn(patch.proposedCode)) {
                secOk = false
                secMsg = "FAIL (Security Test): $reason"
                break
            }
        }

        val lines = patch.proposedCode.lines().size
        val review = "Patch ID    : ${patch.patchId}\n" +
            "Instruction : ${patch.userInstruction}\n" +
            "Module      : ${patch.targetModule}\n" +
            "Line Count  : $lines lines\n" +
            "Status      : Awaiting Mandatory User Code Review Sign-off"

        return ValidationReport(
            errorCheckPassed = errOk,
            errorCheckMessage = errMsg,
            securityTestPassed = secOk,
            securityTestMessage = secMsg,
            codeReviewSummary = review,
            readyForApproval = errOk && secOk
        )
    }
}
