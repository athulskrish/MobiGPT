# MobiGPT 🤖

> Privacy-focused, offline AI chat application for Android with 100% on-device processing

> **Local AI Chat for Android** - Privacy-first, offline-capable AI assistant powered by llama.cpp

[![Android](https://img.shields.io/badge/Android-8.1+-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.2.7-brightgreen.svg)](CHANGELOG.md)
[![Build](https://img.shields.io/badge/Build-Gradle%208.9%20%7C%20AGP%208.7.0-blue.svg)](build.gradle)

## 🌟 Features

- **🔒 100% Local Processing** - All AI inference runs on-device, no data leaves your phone
- **⚡ Multi-Threading** - 3-6x faster inference with optimized thread management  
- **💬 Streaming Responses** - Real-time token-by-token display like ChatGPT
- **📱 Smart UI** - Intelligent auto-scroll that doesn't interrupt reading
- **🎯 Model Management** - Easy model download, selection, and optimization
- **🔋 Battery-Aware** - Adapts performance based on battery level and thermal state
- **🎨 Modern Design** - Material Design with WhatsApp-inspired chat bubbles
- **🌈 Customizable Themes** - 5 accent colors + OLED Black theme for battery saving
- **📊 Performance Monitoring** - Real-time token speed and inference benchmarking
- **🚀 Onboarding Experience** - Guided 5-screen tutorial for first-time users
- **🔐 Security System** - App lock with PIN or biometric, auto-lock, and export authentication
- **📤 Export Conversations** - Export chats to Markdown or PDF with native on-device rendering
- **📈 Token Count Display** - Real-time generation speed and token tracking

## 📚 Quick Start

### Requirements

- Android 8.1+ (API 27, targeting API 36)
- 3GB+ RAM (6GB+ recommended)
- 6GB+ storage for models
- ARMv8-A processor (arm64-v8a)

### Build & Install

```powershell
# Clone repository
git clone https://github.com/athulskrish/MobiGPT.git
cd MobiGPT

# Build
.\gradlew clean assembleDeviceDebug

# Install on device
.\gradlew installDeviceDebug
```

### First Run

1. Complete onboarding tutorial
2. Download a model (TinyLlama 1.1B recommended for testing)
3. Select model from dropdown
4. Start chatting!

## 📖 Documentation

- **[docs/FEATURES.md](docs/FEATURES.md)** - Complete feature guide (themes, typography, search, auto-delete, downloads)
- **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)** - Architecture, build instructions, troubleshooting, and performance tuning
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guide, branching, commit conventions, and development setup
- **[SECURITY.md](SECURITY.md)** - Vulnerability disclosure policy and security architecture
- **[LEGAL.md](LEGAL.md)** - Licensing clearances, third-party software notices, and trademark disclosures
- **[CHANGELOG.md](CHANGELOG.md)** - Version history and changes

## 🏗️ Architecture

```
UI Layer (MainActivity + OnboardingActivity)
    ↓
Business Logic (ChatManager + Threading)
    ↓
AI Engine (LlamaEngine.kt + ModelConfig)
    ↓
JNI Bridge (C++ + Threading Optimization)
    ↓
llama.cpp (Native Inference Engine)
    ↓
Room Database (Chat History + Messages)
```

## 📊 Performance

| Device Type | Threads | Token Speed | Example Model |
|-------------|---------|-------------|---------------|
| Low-end     | 2       | 3-4 tok/s  | TinyLlama 1B  |
| Mid-range   | 4-5     | 6-8 tok/s  | Llama 2 7B    |
| High-end    | 6-7     | 9-15 tok/s | Mistral 7B    |

*Performance varies based on device hardware and model quantization*

### Threading Performance Improvement
- **Single Thread**: 1.5-2 tok/s (baseline)
- **Multi-Thread**: 3-15 tok/s (**2-6x faster**)

## 🎯 Key Technologies

- **Language**: Java + Kotlin
- **AI Engine**: llama.cpp with JNI integration
- **Database**: Room (SQLite) with encryption
- **UI**: Material Design Components
- **Build**: Gradle 8.9 + Android Gradle Plugin 8.7.0
- **NDK**: Version 27.0

## 🤩 Smart Features

### Intelligent Auto-Scroll
- Automatically scrolls during AI response streaming
- Pauses when user scrolls up to read previous messages
- Shows floating action button to return to bottom
- Resumes auto-scroll when user returns to bottom

### Device Optimization
- Automatic thread count detection based on CPU cores
- Battery-aware performance scaling
- Thermal throttling protection
- Memory usage optimization

### Model Management
- One-click model downloads with resume capability
- Automatic model validation
- Smart caching and storage management
- Progress tracking with retry mechanisms

## 📱 Supported Models

| Model Family | Variant | Size | Recommended Device | Highlights |
|-------------|---------|------|-------------------|------------|
| **Qwen 2.5** | General Instruct | 0.5B, 1.5B, 3B, 7B | Any (3GB+) to High-end (8GB+) | Ultra-fast inference, high multilingual accuracy |
| **Qwen 2.5 Coder** | Code Specialist | 0.5B, 1.5B, 3B, 7B | Any (3GB+) to High-end (8GB+) | State-of-the-art open code generation & reasoning |
| **DeepSeek-R1 Distill** | Qwen Reasoning | 1.5B, 7B | Mid-range (4GB+) to High-end (8GB+) | Step-by-step thinking, math & deep logic |
| **Gemma 3** | Multimodal/Multilingual | 1B, 4B, 12B | Any (3GB+) to Flagship (12GB+) | Google's next-gen architecture, 140+ languages |
| **Gemma 2** | General Instruct | 2B, 9B | Mid-range (4GB+) to High-end (8GB+) | High-quality conversational responses |
| **CodeGemma** | Code Assistant | 2B, 7B | Mid-range (4GB+) to High-end (8GB+) | Code completion and refactoring |
| **Llama 3.2 / 3.1** | Meta Instruct | 1B, 3B, 8B | Any (3GB+) to High-end (8GB+) | Meta's latest lightweight and generalist models |
| **Sarvam AI** | Indic Languages | 2B, 3B | Any (3GB+) to Mid-range (6GB+) | Optimized for 10 Indian languages + English |
| **Phi-3 Mini** | Microsoft Efficient | 3.8B | Mid-range (6GB+ RAM) | High efficiency, compact footprint |
| **Custom URL** | Any GGUF | Any | Dependent on model size | Direct Hugging Face download support |

## 🛠️ Development

### Building from Source

```powershell
# Prerequisites
- Android Studio Arctic Fox+
- NDK 27.0
- Java 11+
- Git

# Build commands
.\gradlew clean
.\gradlew :app:assembleDebug
.\gradlew :app:installDebug

# View logs
adb logcat -s LlamaEngine ChatManager MainActivity
```

### Testing

```powershell
# Run unit tests
.\gradlew test

# Run instrumentation tests
.\gradlew connectedAndroidTest

# Performance benchmarking
# (Built into app - check logs for tok/s metrics)
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) for details on setting up your local environment, coding standards, and opening pull requests.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License & Legal

MobiGPT is open-source software licensed under the **MIT License**. See the [LICENSE](LICENSE) file for the full license text and [LEGAL.md](LEGAL.md) for complete third-party software notices, patent/trademark clearances, and privacy declarations.

## 🙏 Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Amazing local LLM inference
- Material Design - UI/UX inspiration
- The open-source community

## 📞 Support

For detailed help, see [Documentation](docs/DEVELOPMENT.md) or open an issue.

---

**Built with ❤️ for privacy-focused AI**