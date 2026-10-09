# Licensing & Legal Clearances

This document provides legal disclosures, licensing terms, third-party software attributions, trademark notices, and regulatory clearances for the **MobiGPT** project.

---

## 1. Project License

MobiGPT is open-source software licensed under the **MIT License**.

```
MIT License

Copyright (c) 2026 Kerala Tech Reach

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 2. Third-Party Software Attributions & Licenses

MobiGPT incorporates and relies upon several open-source libraries and components. All third-party dependencies have been audited to ensure complete license compatibility with our MIT license.

### A. Core AI & Inference Engine
*   **llama.cpp & GGML**
    *   **Copyright:** (c) 2023-2026 Georgi Gerganov and llama.cpp contributors
    *   **License:** MIT License
    *   **Source:** [github.com/ggerganov/llama.cpp](https://github.com/ggerganov/llama.cpp)
    *   **Usage:** Vendored in `app/src/main/cpp/llama.cpp` for native C++ on-device LLM inference.

### B. Database & Encryption
*   **SQLCipher for Android (`net.zetetic:sqlcipher-android`)**
    *   **Copyright:** (c) 2008-2026 Zetetic LLC
    *   **License:** BSD 3-Clause License
    *   **Source:** [github.com/sqlcipher/sqlcipher-android](https://github.com/sqlcipher/sqlcipher-android)
    *   **Usage:** Provides transparent 256-bit AES database encryption for chat and message storage.

### C. Android Jetpack & Google Libraries
*   **AndroidX (AppCompat, Activity, Fragment, ConstraintLayout, RecyclerView, Room, WorkManager, Security-Crypto, Biometric)**
    *   **Copyright:** (c) The Android Open Source Project
    *   **License:** Apache License 2.0
    *   **Source:** [android.googlesource.com](https://android.googlesource.com/platform/frameworks/support)
    *   **Usage:** Standard Android UI, lifecycle management, and architectural components.
*   **Material Components for Android**
    *   **Copyright:** (c) Google LLC
    *   **License:** Apache License 2.0
    *   **Source:** [github.com/material-components/material-components-android](https://github.com/material-components/material-components-android)
    *   **Usage:** Material 3 styling, navigation drawer, chips, and UI components.
*   **JSR 305 Annotations (`com.google.code.findbugs:jsr305`)**
    *   **Copyright:** (c) 2007-2009 JSR-305 expert group
    *   **License:** BSD 3-Clause License
    *   **Usage:** Nullability and static analysis annotations.

### D. UI & Animation Libraries
*   **Lottie for Android (`com.airbnb.android:lottie`)**
    *   **Copyright:** (c) 2018 Airbnb, Inc.
    *   **License:** Apache License 2.0
    *   **Source:** [github.com/airbnb/lottie-android](https://github.com/airbnb/lottie-android)
    *   **Usage:** Vector animations for loading states and splash elements.
*   **TapTargetView (`com.getkeepsafe.taptargetview:taptargetview`)**
    *   **Copyright:** (c) 2016 KeepSafe Software Inc.
    *   **License:** Apache License 2.0
    *   **Source:** [github.com/KeepSafe/TapTargetView](https://github.com/KeepSafe/TapTargetView)
    *   **Usage:** Interactive UI showcase and feature tour.

### E. Kotlin Runtime
*   **Kotlin Standard Library & Kotlin Coroutines (`kotlinx-coroutines-android`)**
    *   **Copyright:** (c) 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors
    *   **License:** Apache License 2.0
    *   **Source:** [github.com/JetBrains/kotlin](https://github.com/JetBrains/kotlin)
    *   **Usage:** Coroutines and asynchronous state handling.

---

## 3. Trademark & Naming Clearances

1.  **Independent Status:** MobiGPT is an independent, community-driven open-source project created by Kerala Tech Reach. MobiGPT is **not** affiliated with, endorsed by, or sponsored by OpenAI, Google LLC, Meta Platforms, Inc., or Microsoft Corporation.
2.  **Android:** "Android", "Google Play", and the Google Play logo are trademarks of Google LLC.
3.  **LLaMA:** "LLaMA", "Llama 2", and "Llama 3" are trademarks or registered trademarks of Meta Platforms, Inc.
4.  **OpenAI / GPT:** "ChatGPT" and "GPT" are trademarks or registered trademarks of OpenAI, LLC. References to "GPT" in MobiGPT are used strictly descriptively to denote generative pre-trained transformer client capabilities.

---

## 4. AI Model Weights & Download Clearances

*   **No Model Weights Bundled:** MobiGPT is an inference client and application engine. The application **does not distribute, host, or bundle proprietary AI model weights** in its source repository or compiled APK releases.
*   **User Responsibility:** Users download GGUF-quantized models (e.g., from Hugging Face or custom URLs) directly to their device storage. Users are responsible for complying with the respective licenses and terms of use of the models they choose to run (such as the Meta Llama Community License, Apache 2.0, or OpenRAIL).

---

## 5. Privacy, Telemetry & Data Clearances

*   **100% On-Device Computation:** MobiGPT performs all natural language processing and token generation locally using on-device CPU/hardware acceleration.
*   **Zero Remote Data Transmission:** No conversation text, prompts, model outputs, or personal information are ever transmitted to any remote servers, cloud endpoints, or third-party APIs.
*   **Zero Telemetry / Analytics:** MobiGPT contains no crash reporting SDKs, tracking pixels, or third-party analytics libraries.
*   **Cryptographic Data Protection:** When database encryption is enabled, user chat history is stored locally in an encrypted SQLite database using SQLCipher with 256-bit AES encryption.

---

## 6. Disclaimer of Warranties & AI Limitations

*   **Probabilistic Outputs:** AI models run via MobiGPT produce outputs generated probabilistically. Outputs may occasionally be inaccurate, incomplete, biased, or inappropriate.
*   **No Professional Advice:** Outputs generated by models running on MobiGPT do not constitute legal, medical, financial, or professional advice.
*   **Warranty Disclaimer:** THE SOFTWARE AND GENERATED OUTPUTS ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS, COPYRIGHT HOLDERS, OR CONTRIBUTORS BE LIABLE FOR ANY CLAIM, DAMAGES, OR LIABILITY ARISING FROM THE USE OF THE SOFTWARE.
