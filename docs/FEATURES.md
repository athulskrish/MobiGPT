# MobiGPT - Feature Documentation

> Complete guide to all features, implementations, and usage

## Table of Contents
- [Theme System](#theme-system)
- [Typography System](#typography-system)
- [Accent Colors](#accent-colors)
- [Search & History](#search--history)
- [Auto-Delete](#auto-delete)
- [Downloads & Notifications](#downloads--notifications)
- [Export Conversations](#export-conversations)
- [Token Count Display](#token-count-display)
- [Security Features](#security-features)
- [Settings](#settings)

---

## Theme System

### Overview
MobiGPT supports Light, Dark, and OLED Black themes with automatic system theme detection.

### Key Features
- **System Theme Auto-Detection** - Follows device settings
- **OLED Black Mode** - Pure black (#000000) for battery saving on OLED screens
- **Dynamic Accent Colors** - 5 color options (Blue, Green, Purple, Orange, Red)
- **Context Menu Theming** - Dark-aware context menus for text selection

### Settings Location
`Settings → Appearance → Theme`

### Implementation Details
- `ThemeManager.java` - Central theme management
- Applies themes before `onCreate()` for flicker-free transitions
- Toolbar, status bar, and drawer header use theme-aware gradients
- All UI components update dynamically on theme change

### Usage
```java
ThemeManager themeManager = new ThemeManager(context);
themeManager.applyTheme();
int accentColor = themeManager.getAccentColor();
```

---

## Typography System

### Overview
Customizable font system with size, family, and spacing controls.

### Key Features
- **Font Sizes**: Small (14sp), Medium (16sp), Large (18sp), Extra Large (20sp)
- **Font Families**: System, Sans-Serif, Serif, Monospace, Roboto
- **Monospace Code** - Toggle for code blocks
- **Global Application** - Applied to all text views throughout the app

### Settings Location
`Settings → Appearance → Typography`

### Implementation Details
- `TypographyManager.java` - Central typography control
- Applies to messages, UI elements, preferences, and code blocks
- Updates dynamically without app restart
- Respects accessibility settings

### Usage
```java
TypographyManager typography = new TypographyManager(context);
typography.applyToTextView(textView);
typography.applyToCodeBlock(codeBlock);
```

---

## Accent Colors

### Available Colors
1. **Blue** (#2196F3) - Default Material Blue
2. **Green** (#4CAF50) - Success and growth
3. **Purple** (#9C27B0) - Creative and unique
4. **Orange** (#FF9800) - Energetic and warm
5. **Red** (#F44336) - Bold and passionate

### Application Areas
- Toolbar gradient overlays
- Drawer header accents
- Download buttons (pause/resume/cancel)
- Progress bars
- FAB (Floating Action Button)
- Switch toggles and checkboxes

### Dynamic Updates
All accent colors update immediately without restarting the activity. The system broadcasts accent color changes to all active components.

---

## Search & History

### Search Functionality
- **In-Chat Search** - Search within current conversation
- **Global Search** - Search across all chats
- **Search Suggestions** - Based on search history
- **Match Navigation** - Previous/Next match buttons

### Search History
- Automatically saves recent search queries
- Configurable history size (default: 50 queries)
- Clear history option in settings
- Privacy: Can be disabled in settings

### Settings Location
`Settings → Chat Settings → Search History`

### Features
- Save last 50 searches (configurable)
- Smart suggestions as you type
- Remove individual history items
- Clear all history with confirmation
- Enable/disable search history saving

---

## Auto-Delete

### Overview
Automatic deletion of old messages to manage storage and privacy.

### Key Features
- **Time-Based Deletion** - Delete messages after 30/60/90/180 days
- **Star Protection** - Starred messages are never deleted
- **Manual Trigger** - Delete old messages on-demand
- **Background Scheduling** - Runs daily at 2 AM

### Settings Location
`Settings → Chat Settings → Message Management`

### Options
- Enable/Disable auto-delete
- Set deletion period (30/60/90/180 days)
- Trigger immediate deletion
- View statistics before deletion

### How It Works
```
Daily Job (2 AM)
  ↓
Check messages > X days old
  ↓
Skip starred messages
  ↓
Delete eligible messages
  ↓
Update chat summaries
```

---

## Downloads & Notifications

### Download Features
- **Resume Support** - Pause and resume downloads
- **WiFi Requirement** - Optionally require WiFi for large downloads
- **Progress Notifications** - Real-time download progress
- **Speed Monitoring** - Display download speed
- **Error Handling** - Automatic retry with exponential backoff

### Notification Types
1. **Download Progress** - Shows during active download
2. **Download Complete** - Notification with optional sound
3. **Download Failed** - Error notification with retry option
4. **Download Paused** - When waiting for WiFi

### Settings Location
`Settings → Downloads & Storage`

### Configuration
- Enable/disable download notifications
- Download complete sound
- Show progress notifications
- WiFi-only downloads
- Concurrent download limit (1-3)

### WiFi Management
- Automatic pause when WiFi disconnected (if required)
- Automatic resume when WiFi reconnects
- Override option for mobile data
- Background monitoring for paused downloads

---

## Export Conversations

### Overview
Export individual conversations or all chats to multiple formats for backup and sharing.

### Export Formats
1. **Markdown (.md)** - Clean text format with formatting
2. **PDF (.pdf)** - Professional color-coded layout with proper formatting

### Export Options
- **Single Conversation**: Export current chat
  - Menu → Export → Choose format (Markdown/PDF)
  - Menu → Export → Share (exports then opens share dialog)
- **All Conversations**: Export all chats to a single PDF
  - Settings → Data & Privacy → Export All Chats

### Features
- **Professional Formatting**
  - Title page with export date and metadata
  - Color-coded messages (User: blue, AI: green)
  - Timestamps for each message
  - Clear visual separators between conversations
- **Smart File Management**
  - Automatic cleanup (keeps 10 most recent exports)
  - Smart file naming with timestamps
  - Saves to Downloads folder
- **Security**
  - Optional authentication before export (PIN/Biometric)
  - Settings → Security → Authenticate for Export
- **Sharing**
  - Share via Android intent to any compatible app
  - FileProvider for secure sharing
- **Background Processing**
  - Smooth UX with progress indicators
  - Non-blocking export operations

### Settings Location
- Single export: Chat menu → Export
- All chats: `Settings → Data & Privacy → Export All Chats`
- Authentication: `Settings → Security → Authenticate for Export`

---

## Token Count Display

### Overview
Real-time performance monitoring showing AI generation speed after each response.

### Features
- **Generation Speed**: Displays tokens per second (e.g., "10.5 token/s")
- **Automatic Tracking**: Token count and timing captured during generation
- **Visual Design**: Subtle display using accent color, positioned near timestamp
- **Smart Display**: Only shown for completed responses (hidden during streaming)

### Technical Details
- Tracks total tokens generated per response
- Measures generation time in milliseconds
- Calculates speed: `tokens_per_second = (tokenCount × 1000) / generationTimeMs`
- Stored in database for historical reference

### Usage
After an AI response completes, you'll see the generation speed:
```
12:30 PM  10.5 token/s  ⭐
```

This helps you:
- Monitor model performance
- Compare different models
- Identify performance issues
- Understand device capabilities

---

## Security Features

### App Lock
Protect your conversations with PIN or biometric authentication.

#### Features
- **Lock Types**: PIN (4-6 digits) or Biometric (Fingerprint/Face)
- **Auto-Lock Timeout**: Immediately, 30s, 1m, 5m, 15m, 30m
- **Lock on App Switch**: Optional immediate lock when switching apps
- **Failed Attempt Protection**: 5 wrong attempts = 30 second lockout

#### Setup
1. Settings → Security → App Lock (toggle ON)
2. Set up PIN (4-6 digit numeric)
3. Choose Lock Type (PIN or Biometric)
4. Configure timeout settings

### Data Protection
Additional security for sensitive operations.

#### Export Authentication
Require authentication before exporting conversations:
- Settings → Security → Data Protection → Authenticate for Export
- Uses PIN or biometric before any export operation
- Applies to single and bulk exports

#### Security Reset
Complete security wipe to start fresh:
- Settings → Security → Data Protection → Reset Security Settings
- Clears PIN, authentication data, and disables app lock
- Confirmation dialog to prevent accidental reset

### Security Implementation
- **PIN Security**: SHA-256 hashing with random salt
- **Encrypted Storage**: AndroidX Security library for preferences
- **Screenshot Blocking**: Lock screen protected from screenshots
- **Biometric**: BIOMETRIC_STRONG authentication level
- **No Bypass**: Back button disabled on lock screen

---

## Settings

### Categories

#### Downloads & Storage
- Storage usage viewer
- Clear cache
- WiFi requirements
- Notification preferences
- Concurrent downloads

#### Chat Settings
- Auto-delete configuration
- Search history management
- Message limits
- Cache size

#### Appearance
- Theme mode (Light/Dark/System)
- Accent colors
- OLED black theme
- Typography settings

#### Data & Privacy
- Export conversations (Markdown/PDF)
- Export all chats (PDF)
- Clear chat history
- Clear starred messages
- Clear all data

#### Security
- App Lock (PIN/Biometric)
- Lock Type selection
- Auto-lock timeout
- Lock on app switch
- Export authentication
- Reset security settings

#### Accessibility
- High contrast mode
- Reduced motion
- Large touch targets
- TalkBack optimization

#### About
- App version
- Open source licenses
- Credits
- Contact information

### Implementation
- Organized preference screens with categories
- Real-time updates without restart
- Validation for critical settings
- Confirmation dialogs for destructive actions

---

## Bug Fixes & Improvements

### Dark Theme Fixes
- ✅ Context menu text visibility in dark mode
- ✅ Download button contrast in dark theme
- ✅ Progress bar visibility
- ✅ Menu text colors

### Accent Color Fixes
- ✅ Download buttons respect accent color
- ✅ Toolbar gradient updates dynamically
- ✅ Drawer header accent application
- ✅ Dynamic color broadcasting

### Crash Fixes
- ✅ MessageAdapter selection mode IndexOutOfBounds
- ✅ Null pointer in message regeneration
- ✅ Thread safety in chat manager

### UI/UX Improvements
- ✅ Overflow menu visibility in all themes
- ✅ Download progress visibility improvements
- ✅ Smooth theme transitions
- ✅ Enhanced empty states

---

## Quick Reference

### Key Files
- `MainActivity.java` - Main chat interface
- `SettingsActivity.java` - Settings management
- `ThemeManager.java` - Theme system
- `TypographyManager.java` - Font system
- `ModelDownloadManager.java` - Download handling
- `ChatManager.java` - Chat operations

### Preference Keys
```
theme_mode: "light" | "dark" | "system"
accent_color: "blue" | "green" | "purple" | "orange" | "red"
oled_black_theme: boolean
font_size: "small" | "medium" | "large" | "extra_large"
font_family: "system" | "sans_serif" | "serif" | "monospace" | "roboto"
delete_old_messages: boolean
delete_messages_after: "30" | "60" | "90" | "180"
search_history_enabled: boolean
download_notifications: boolean
require_wifi: boolean
```

---

## Testing Checklist

### Theme System
- [ ] Switch between Light/Dark/System modes
- [ ] Enable OLED Black theme in dark mode
- [ ] Change accent colors in all themes
- [ ] Verify toolbar gradient updates
- [ ] Check context menu visibility
- [ ] Test drawer header appearance

### Typography
- [ ] Change font size (all 4 options)
- [ ] Switch font families (all 5 options)
- [ ] Toggle monospace code
- [ ] Verify message rendering
- [ ] Check settings text size
- [ ] Test accessibility

### Downloads
- [ ] Download model with WiFi
- [ ] Pause and resume download
- [ ] Test WiFi requirement
- [ ] Verify notifications
- [ ] Check progress updates
- [ ] Test cancel functionality

### Search
- [ ] Search in current chat
- [ ] Global search across chats
- [ ] Use search suggestions
- [ ] Navigate matches
- [ ] Clear search history
- [ ] Disable search history

### Auto-Delete
- [ ] Enable auto-delete
- [ ] Trigger manual delete
- [ ] Verify starred message protection
- [ ] Check deletion statistics
- [ ] Test different time periods

---

**For detailed build instructions, see [BUILD_GUIDE.md](BUILD_GUIDE.md)**  
**For troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md)**  
**For security info, see [SECURITY.md](SECURITY.md)**
