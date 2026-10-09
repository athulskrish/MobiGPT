# Contributing to MobiGPT

Thank you for your interest in contributing to **MobiGPT**! We welcome contributions from developers, researchers, and designers of all experience levels to help make local, private, on-device AI accessible to everyone.

Please review this guide before submitting issues or pull requests.

---

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to [info@keralatechreach.in](mailto:info@keralatechreach.in).

---

## Development Environment Setup

### Prerequisites

*   **Operating System:** Windows, macOS, or Linux
*   **Java Development Kit (JDK):** JDK 17 or higher
*   **Android Studio:** Android Studio Koala / Ladybug or newer
*   **Android SDK:** Android SDK Platform 36 (`compileSdk 36`, `targetSdk 36`)
*   **Android NDK:** Version `27.0.12077973`
*   **CMake:** Version `3.22.1` or higher
*   **Git:** Version 2.30+

### Clone & Build

1.  **Fork and clone the repository:**
    ```bash
    git clone https://github.com/<your-username>/MobiGPT.git
    cd MobiGPT
    ```

2.  **Configure Android SDK:**
    Ensure `local.properties` exists at the project root pointing to your Android SDK path (Android Studio creates this automatically):
    ```properties
    sdk.dir=/path/to/your/Android/sdk
    ```

3.  **Build the debug APK:**
    ```bash
    # On Linux/macOS
    ./gradlew clean assembleDeviceDebug

    # On Windows PowerShell
    .\gradlew clean assembleDeviceDebug
    ```

4.  **Install on a connected device:**
    ```bash
    .\gradlew installDeviceDebug
    ```

---

## Development Workflow

### Branching Strategy

*   `Stable` / `main`: Production-ready, stable releases.
*   Feature & bugfix branches: Branch off `Stable` using descriptive names:
    *   `feature/model-quantization-selector`
    *   `fix/chat-history-scroll-lag`
    *   `docs/update-build-instructions`

### Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) standard:

```text
<type>(<optional scope>): <short summary>

[optional body]

[optional footer(s)]
```

**Common Types:**
*   `feat`: A new user-facing feature.
*   `fix`: A bug fix.
*   `docs`: Documentation changes only.
*   `refactor`: Code restructuring without changing functionality or fixing bugs.
*   `perf`: Performance optimizations.
*   `chore`: Changes to build scripts, dependencies, or tool configurations.

**Example:**
```text
feat(inference): add support for Qwen 2.5 ChatML template

Implement prompt formatting for Qwen 2.5 models and adjust
context window token calculations.
```

---

## Code Quality & Style Guidelines

### 1. Formatting
*   We use [.editorconfig](.editorconfig) to standardize indentation, charset, and newlines.
*   **Java & Kotlin:** 4 spaces indentation, no tab characters, UTF-8 encoding.
*   **XML & Gradle:** 4 spaces indentation.
*   **Naming Conventions:** Standard Android naming conventions (`camelCase` for variables/methods, `PascalCase` for classes).

### 2. Threading & Safety
*   **Inference & JNI:** All llama.cpp native operations **must** run on dedicated background threads (`runLoop` / `Dispatchers.IO`). Never invoke native JNI calls on the main/UI thread.
*   **Database:** All Room database transactions must execute on background executors.
*   **Resource Cleanup:** Native contexts, models, and batch handles must be explicitly freed when unloading models or tearing down engines.

### 3. Verification & Linting
Before opening a pull request, verify that your changes compile and pass static analysis:

```bash
# Run Android Lint
.\gradlew lintDeviceDebug

# Run unit tests
.\gradlew test
```

---

## Submitting Pull Requests

1.  **Push your feature branch:**
    ```bash
    git push origin feature/your-feature-name
    ```
2.  **Open a Pull Request:** Go to GitHub and click **Compare & pull request**.
3.  **Provide a clear description:**
    *   Explain the problem or motivation for the change.
    *   Detail what changed and why.
    *   Attach before/after screenshots or screen recordings for UI changes.
    *   List which devices/emulators and Android versions you tested on.
4.  **Respond to reviews:** Address reviewer feedback promptly.

---

## Need Help?

*   Check our [Developer Guide](docs/DEVELOPMENT.md) for architecture and troubleshooting details.
*   Check our [Features Overview](docs/FEATURES.md) for existing capabilities.
*   Contact the maintainers at [info@keralatechreach.in](mailto:info@keralatechreach.in) or open a GitHub discussion.
