# InOut – Personal Expense Tracker

**InOut** is a fast, offline-first personal expense tracker built natively for Android using Kotlin and Jetpack Compose. It is designed to help Indian users organize their daily cash flow, track loans and peer debts, and generate clean financial summaries to aid in tax preparation.

### ⚠️ Important Disclaimer
**InOut is NOT tax advice and is NOT a statutory accounting system.** 
This app is a personal tracking tool. It does not strictly adhere to ICAI Indian Accounting Standards or enterprise double-entry compliance. The generated PDF reports are meant strictly as a personal aid for preparing your Income Tax Return (ITR). Always verify your figures with your official bank statements, Form 16, AIS, and Form 26AS before filing. For complex tax cases, consult a Chartered Accountant.

---

### ✨ Features
*   **Strict Ledger Math:** A robust underlying engine ensures money doesn't appear out of thin air. Transfers, loans, and peer lending are tracked precisely.
*   **Privacy & Offline-First:** Your data is yours. There are no cloud servers, no user accounts, and no telemetry. Everything is stored locally on your device via Room/SQLite.
*   **ML Kit Receipt Parsing:** Extract amounts and merchants natively on-device from images and PDFs without sending your data to a server.
*   **ITR-Aid PDF Reports:** Generate clean, paginated PDF dossiers mapping your entries to standard balance sheet layouts and Income/Expense statements.
*   **Encrypted Backups:** Export your entire vault as a cryptographic archive for safe physical storage or device migration.

### 🛠 Tech Stack
*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Material 3)
*   **Architecture:** MVVM, Coroutines, Flow
*   **Local Storage:** Room Database
*   **On-Device ML:** Google ML Kit (Text Recognition)
*   **Build System:** Gradle KTS

### 🚀 Build Instructions
1. Clone the repository: `git clone https://github.com/snj147/InOut.git`
2. Open the project in Android Studio (Ladybug / Koala or newer).
3. Sync Gradle files.
4. Run the app on a physical device or emulator running Android 8.0 (API 26) or higher.

### 🔒 Privacy Policy
InOut does not collect, transmit, or share any personal or financial data. All inputs, images, and ledgers remain strictly on your local device. If you choose to send a bug report via the in-app feedback tool, it uses a one-way Telegram bot integration that only transmits the exact text/screenshot you explicitly consent to send.
