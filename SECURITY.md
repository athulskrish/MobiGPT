# Security Policy

MobiGPT takes security and user privacy seriously. As a 100% offline, on-device AI application, protecting local cryptographic data and model execution integrity is our highest priority.

---

## Supported Versions

Only the latest release receive active security patches. We recommend all users and contributors stay on the latest version.

| Version | Supported          |
| ------- | ------------------ |
| 1.2.x   | :white_check_mark: |
| < 1.2.0 | :x:                |

---

## Reporting a Vulnerability

**Please do NOT report security vulnerabilities through public GitHub issues, discussions, or pull requests.**

If you discover a security vulnerability or sensitive data issue in MobiGPT, report it responsibly via our private channels:

1.  **GitHub Private Vulnerability Reporting (Preferred):**
    If enabled on the repository, navigate to the **Security** tab of this repository and click **Report a vulnerability**.

2.  **Email:**
    Send an encrypted or private email directly to:
    **[info@keralatechreach.in](mailto:info@keralatechreach.in)** with the subject line:
    `[SECURITY] MobiGPT Vulnerability Report - <Short Description>`

### What to Include in Your Report

To help us investigate and remediate the issue quickly, please include:
*   A clear description of the vulnerability and its potential impact.
*   The version of MobiGPT and Android OS where the issue was reproduced.
*   Step-by-step reproduction instructions or a minimal Proof of Concept (PoC).
*   Any suggested mitigations or patches, if available.

### Response Timeline

*   **Acknowledgement:** We will acknowledge receipt of your vulnerability report within **48 hours**.
*   **Assessment & Fix:** We will provide an assessment and status update within **7 business days**.
*   **Coordinated Disclosure:** We request that you maintain confidentiality until we have developed and released a patch. We will credit researchers in our release notes unless you request anonymity.

---

## Security Architecture Highlights

*   **Zero Remote Data Transmission:** All LLM inference executes completely on the local CPU/NPU. Conversation data never touches any remote network server.
*   **Database Encryption:** Chat history and messages are stored using SQLCipher with 256-bit AES encryption.
*   **Key Protection:** Cryptographic keys are anchored to the Android Keystore system.
*   **No Analytics or Tracking:** No third-party analytics SDKs, trackers, or telemetry services are bundled in the application.
