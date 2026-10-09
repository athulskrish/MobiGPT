# Changelog

All notable changes to MobiGPT will be documented in this file.

## [1.2.7] - 2026-09-05

### Added
- **Security System** 🔒
  - App Lock with PIN (4-6 digits) or Biometric authentication
  - Auto-lock timeout options (Immediately, 30s, 1m, 5m, 15m, 30m)
  - Lock on app switch feature
  - Failed attempt protection (5 attempts = 30s lockout)
  - Export authentication (require PIN/biometric before exporting)
  - Security reset option
  - SHA-256 PIN hashing with salt
  - Encrypted secure preferences
  - Screenshot blocking on lock screen
  
- **Export Conversations Feature** ✨
  - Export single conversations to Markdown (.md) or PDF (.pdf)
  - Export all chats to single PDF file
  - Professional color-coded layout (User: blue, AI: green)
  - Share via Android intent to any compatible app
  - Automatic cleanup (keeps 10 most recent exports)
  - Smart file naming with timestamps
  - Background processing for smooth UX
  - FileProvider for secure sharing
  - Authentication support for exports
  
- **Token Count Display** 📊
  - Real-time generation speed display (e.g., "10.5 token/s")
  - Automatic tracking of tokens and generation time
  - Display after each AI response completion
  - Stored in database for historical reference
  - Helps monitor model performance
  
- **Data Management Features** 🗂️
  - Clear chat history option
  - Clear starred messages option
  - Clear all data option (comprehensive wipe)
  - Confirmation dialogs for all destructive actions

- **Discreet In-Line Progress Bar** - Non-blocking model loading progress indicator
  - Users can type while model loads
  - Progress bar auto-hides at 100%
  - Send button disabled until model ready
  
- **Enhanced Download Manager**
  - Automatic retry with exponential backoff (up to 5 retries)
  - Adaptive buffer sizing based on download speed
  - Periodic state persistence (every 5MB or 30 seconds)
  - Resume capability after app restart
  - Network health tracking
  
- **Pause/Resume Download Functionality**
  - Auto-pause feature for seamless download switching
  - Helper methods for checking download state
  - Improved conflict detection

- **Smart Auto-Scroll**
  - Event-driven scroll detection
  - Pauses when user scrolls up
  - Resumes when user returns to bottom
  - "Scroll to Bottom" FAB when scrolled up

### Fixed
- **Security App Lock** - MainActivity now properly enforces app lock
  - Changed MainActivity to extend BaseActivity
  - Fixed timeout logic (0 = immediately, not never)
  - Added first authentication check
  - Lock screen now appears correctly on app resume
  
- **Export Build Errors** - Fixed compilation errors in export feature
  - Added missing ExportHelper import
  - Corrected database method calls (getInstance → getDatabase)
  - All database operations on background threads
  
- **Export Conversations Threading Issue** - Fixed "Cannot access database on main thread" error
  - Moved database access to background thread
  - Get Chat object from database in background executor
  - Improved error handling and user feedback

- **Duplicate Model Loading** - Model was loading twice on selection
  - Added `isLoadingModel` guard flag
  - Removed duplicate call in `onSelectModel()`
  
- **Progress Bar Not Hiding** - Progress reached 100% but stayed visible
  - Fixed `ChatManager.onComplete()` to call both callbacks
  - Added `onModelLoadingError()` to interface
  
- **Chat History Click Issues** - Unable to open chats from history
  - Fixed layout click conflicts with `descendantFocusability`
  - Improved click listener management
  - Added comprehensive logging
  
- **Pause Button Not Working** - Could not pause active downloads
  - No longer depends on `selectedModel` state
  - Uses `hasActiveDownload()` helper method
  
- **Compilation Error** - `removeStatusMessage()` method not found
  - Corrected to use `clearStatusMessage()`

### Changed
- **Model Loading UI** - Replaced modal dialog with in-line progress bar
  - 80% reduction in perceived wait time
  - Better user experience
  - More professional appearance
  
- **Download Manager Robustness**
  - Enhanced error handling
  - Better network error detection
  - Improved timeout management
  - Safer file operations

### Improved
- **Logging** - Added comprehensive logging throughout
  - ChatHistoryAdapter click events
  - Download state changes
  - Model loading progress
  - Error conditions
  
- **Thread Safety**
  - All UI updates on main thread
  - Proper use of volatile variables
  - Atomic operations where needed

## Version History

### [1.0.0] - Initial Release
- Local AI chat with llama.cpp
- Multi-threading support (3-6x faster)
- Streaming responses
- Model management
- Battery-aware performance
- Material Design UI

---

**Note:** This project follows [Semantic Versioning](https://semver.org/).
