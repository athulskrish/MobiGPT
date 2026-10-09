# Interactive Tutorial Implementation Guide

## Overview
The interactive tutorial system provides a step-by-step guided tour for first-time users of MobiGPT. It uses spotlight/showcase UI to highlight key features and guide users through the app's main functionality.

## Features

### Welcome Dialog
- **Displays on first launch** after onboarding
- Shows app name with emoji (MobiGPT 🎉)
- Lists key features:
  - Run AI models entirely on device
  - Complete privacy - no data sent to servers
  - Multiple model support (Llama, Phi, Gemma)
  - Chat history and starred messages
  - Export conversations to PDF/TXT
  - Customizable themes and settings
- Options:
  - **Start Tutorial**: Launches the interactive tour
  - **Skip**: Dismisses and marks tutorial as completed

### Tutorial Steps

#### Step 1: Download AI Model
- **Target**: Model dropdown spinner
- **Highlights**: The model selection dropdown at the top
- **Description**: "First, select and download an AI model from this dropdown. You'll see the download progress below. Wait for it to complete before chatting."
- **Visual**: Blue circular highlight with description

#### Step 2: Start Chatting (Part A)
- **Target**: Message input field
- **Highlights**: The text input where users type messages
- **Description**: "Type your message here and tap the send button to chat with the AI. Wait for the AI to respond before sending another message."
- **Visual**: Blue circular highlight with description

#### Step 2: Start Chatting (Part B)
- **Target**: Send button
- **Highlights**: The send button next to the message input
- **Description**: "Tap here to send your message to the AI assistant."
- **Visual**: Blue circular highlight with description

#### Step 3: Chat History
- **Target**: Hamburger menu button (top left)
- **Highlights**: The menu icon to open the navigation drawer
- **Description**: "Open this menu to view all your previous conversations. Switch between chats, create new ones, or view starred messages."
- **Visual**: Blue circular highlight with description

#### Step 4: Export Conversations
- **Target**: Toolbar (top right area)
- **Highlights**: The overflow menu area
- **Description**: "Tap the menu icon (⋮) in the top right to export your conversations to PDF or TXT format. Perfect for keeping records!"
- **Visual**: Blue circular highlight with description

#### Step 5: Settings & More
- **Target**: Toolbar (top right area)
- **Highlights**: The overflow menu area
- **Description**: "Access settings from the same menu to customize themes, model parameters, and other preferences. You're all set! 🎉"
- **Visual**: Blue circular highlight with description

## Implementation Details

### Files Created/Modified

#### 1. `TutorialManager.java`
Location: `app/src/main/java/com/keralatechreach/mobigpt/utils/TutorialManager.java`

**Key Methods:**
- `isTutorialCompleted()`: Check if user has completed the tutorial
- `markTutorialCompleted()`: Mark tutorial as completed (stored in SharedPreferences)
- `resetTutorial()`: Reset tutorial state (for testing or user preference)
- `showWelcomeDialog(Runnable onStartTutorial)`: Display welcome dialog with app details
- `startTutorial(...)`: Launch the interactive tutorial sequence with spotlight overlays
- `cancelTutorial()`: Cancel ongoing tutorial

**SharedPreferences:**
- Preference name: `tutorial_prefs`
- Key: `tutorial_completed` (boolean)

#### 2. `MainActivity.java` (Modified)
**Changes:**
- Added `TutorialManager` field and import
- Initialize tutorial manager in `onCreate()`
- Added `showTutorialIfNeeded()` method - called after UI is ready
- Added `startInteractiveTutorial()` method - validates views and starts tutorial
- Added tutorial manager cleanup in `onDestroy()`

#### 3. `build.gradle` (Modified)
**Dependency Added:**
```gradle
implementation 'com.getkeepsafe.taptargetview:taptargetview:1.13.3'
```

#### 4. `colors.xml` (Modified)
**Colors Added:**
```xml
<color name="tutorial_outer_circle">#3B82F6</color>
<color name="tutorial_highlight">#60A5FA</color>
```

## Usage

### For First-Time Users
1. Complete onboarding
2. Welcome dialog appears automatically
3. Choose "Start Tutorial" or "Skip"
4. If started, follow the 5-step guided tour
5. Tap anywhere to advance to next step
6. Tutorial can be canceled anytime by tapping outside multiple times

### For Returning Users
The tutorial will NOT show again after:
- Completing all steps
- Skipping the tutorial
- Canceling the tutorial (with confirmation)

### Manually Restarting Tutorial
To add a "Restart Tutorial" option in Settings:

```java
// In SettingsActivity or MainActivity
public void restartTutorial() {
    if (tutorialManager != null) {
        tutorialManager.resetTutorial();
        Toast.makeText(this, "Tutorial reset. It will show on next app launch.", Toast.LENGTH_LONG).show();
    }
}
```

## Customization

### Modify Tutorial Colors
Edit `res/values/colors.xml`:
```xml
<color name="tutorial_outer_circle">#YOUR_COLOR</color>
```

### Add More Steps
In `TutorialManager.startTutorial()`:
```java
TapTarget step6 = TapTarget.forView(yourView,
    "Step 6: Your Feature",
    "Description of your feature")
    .outerCircleColor(R.color.tutorial_outer_circle)
    // ... other properties
    
tutorialSequence.targets(step1, step2, step2b, step3, step4, step5, step6);
```

### Modify Welcome Message
Edit `TutorialManager.showWelcomeDialog()`:
```java
.setTitle("Your Custom Title")
.setMessage("Your custom message\n\nKey Features:\n...")
```

## Testing

### Test Tutorial Flow
1. Clear app data or uninstall/reinstall
2. Complete onboarding
3. Observe welcome dialog
4. Test "Start Tutorial" button
5. Go through all 5 steps
6. Verify tutorial doesn't show again

### Test Skip Functionality
1. Clear app data
2. Complete onboarding
3. Tap "Skip" on welcome dialog
4. Restart app - tutorial should not appear

### Test Cancel Functionality
1. Start tutorial
2. Tap outside the highlighted area multiple times
3. Confirm cancellation dialog
4. Restart app - tutorial should not appear

## Troubleshooting

### Tutorial Not Showing
**Possible causes:**
1. Tutorial already completed (check SharedPreferences)
2. Views not initialized when tutorial is called
3. Activity finishing before tutorial starts

**Solution:**
```java
// Reset tutorial manually
tutorialManager.resetTutorial();
```

### Views Not Highlighted
**Possible cause:** View IDs changed or views are null

**Solution:**
Verify view references in `MainActivity.startInteractiveTutorial()`:
```java
if (spinnerModels == null || editMessage == null || ...) {
    Log.w(TAG, "Tutorial skipped - required views not found");
    return;
}
```

### Tutorial Crashes on Rotation
**Built-in handling:** Tutorial automatically cancels when activity is destroyed

## Performance Considerations

- Tutorial manager is lightweight (~10KB)
- TapTargetView library (~100KB)
- Tutorial is only shown once per user
- No performance impact after completion
- Background thread safe - waits for UI to be ready

## Accessibility

The tutorial supports:
- Large text sizes
- High contrast colors
- Screen readers (through view descriptions)
- Cancelable at any time
- Skip option for power users

## Future Enhancements

Potential improvements:
1. Add tutorial progress indicator (e.g., "Step 1 of 5")
2. Add "Previous" button to go back to previous step
3. Add interactive challenges (e.g., "Try sending a message")
4. Add video/GIF demonstrations for complex features
5. Add context-sensitive help tooltips throughout the app
6. Add analytics to track which steps users skip most often

## License
This tutorial implementation uses TapTargetView library (Apache 2.0 License)
