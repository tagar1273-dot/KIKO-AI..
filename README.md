# Kiko AI — Native Android App (Kotlin + Material 3 GUI)

Complete Android Studio / Gradle project that compiles directly into an **Android `.apk`** with a native 4-tab Material Dark GUI implementing all your requirements:

1. **Hybrid AI (`HybridAIEngine.kt`)**: Cloud LLM API + Local Offline Commands (`status`, `time`, `version`, `help`).
2. **Instruction-Only Code Modification & Pre-Release Gates (`PreReleaseQualityGate.kt`)**:
   - Enforces explicit user instruction
   - Automated **Error Checking** & **Security Testing**
   - Mandatory **Code Review** sign-off
3. **Backup, Approval & Version History (`GovernanceManager.kt`)**:
   - Automatic snapshot in `kiko_backups/` *before* any update
   - Creates new version *after* explicit user approval
   - Saves immutable `version_history.json`
4. **Android-Approved APK Installer (`AndroidApprovedApkInstaller.kt`)**:
   - Uses official `PackageInstaller.Session` + `STATUS_PENDING_USER_ACTION` system prompt with `REQUEST_INSTALL_PACKAGES`.

## How to Compile into `.apk`
- **Option A (Android Studio):** Open `NEXUS_AI_Android_App` in Android Studio -> **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
- **Option B (No PC / Cloud Auto-Build):** Upload this folder to a GitHub repository -> Go to **Actions > Build Android APK** -> Download the compiled `app-debug.apk`.
