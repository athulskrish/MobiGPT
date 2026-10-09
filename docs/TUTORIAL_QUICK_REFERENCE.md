# Interactive Tutorial - Quick Reference

## What Was Implemented

A **5-step interactive tutorial** for MobiGPT that guides first-time users through the app's key features using spotlight/showcase UI.

## Tutorial Flow

```
First Launch (After Onboarding)
         ↓
Welcome Dialog (App Details)
         ↓
   [Start Tutorial] or [Skip]
         ↓
Step 1: Download Model (Highlight model dropdown)
         ↓
Step 2a: Type Message (Highlight input field)
         ↓
Step 2b: Send Button (Highlight send button)
         ↓
Step 3: Chat History (Highlight hamburger menu)
         ↓
Step 4: Export (Highlight top right menu)
         ↓
Step 5: Settings (Highlight top right menu)
         ↓
    Tutorial Complete ✓
```

## Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `TutorialManager.java` | `utils/` | Manages tutorial logic and UI |
| TapTargetView Library | `build.gradle` | Provides spotlight overlay |
| Tutorial Colors | `colors.xml` | Blue theme for highlights |
| Integration | `MainActivity.java` | Launches tutorial after UI ready |

## How to Use

### As a User
- **First time**: Tutorial shows automatically after onboarding
- **Skip option**: Click "Skip" on welcome dialog
- **Cancel anytime**: Tap outside highlighted area
- **Won't show again**: After completion, skip, or cancel

### As a Developer

#### Reset Tutorial (for testing)
```java
tutorialManager.resetTutorial();
```

#### Check if Completed
```java
boolean completed = tutorialManager.isTutorialCompleted();
```

#### Manually Trigger
```java
tutorialManager.showWelcomeDialog(() -> {
    startInteractiveTutorial();
});
```

## Files Modified

1. ✅ `build.gradle` - Added TapTargetView library
2. ✅ `TutorialManager.java` - Created (new file)
3. ✅ `MainActivity.java` - Integrated tutorial calls
4. ✅ `colors.xml` - Added tutorial colors
5. ✅ `INTERACTIVE_TUTORIAL_GUIDE.md` - Full documentation
6. ✅ `TUTORIAL_QUICK_REFERENCE.md` - This file

## Build & Run

1. **Sync Gradle**: The TapTargetView library will download
2. **Build**: `./gradlew assembleDebug`
3. **Test**: Clear app data and launch app

```bash
# Clear app data (for testing)
adb shell pm clear com.keralatechreach.mobile_llm5

# Install and run
./gradlew installDebug
adb shell am start -n com.keralatechreach.mobile_llm5/.MainActivity
```

## Customization

### Change Highlight Color
```xml
<!-- res/values/colors.xml -->
<color name="tutorial_outer_circle">#FF5722</color>
```

### Modify Welcome Message
```java
// TutorialManager.java - showWelcomeDialog()
.setTitle("Welcome to MobiGPT! 🎉")
.setMessage("Your custom message here...")
```

### Add New Step
```java
// TutorialManager.java - startTutorial()
TapTarget step6 = TapTarget.forView(newView,
    "Step 6: New Feature",
    "Description here")
    .outerCircleColor(R.color.tutorial_outer_circle)
    // ... configure styling
    
tutorialSequence.targets(step1, step2, step2b, step3, step4, step5, step6);
```

## Storage

Tutorial completion status is stored in SharedPreferences:
- **File**: `tutorial_prefs`
- **Key**: `tutorial_completed` (boolean)
- **Location**: `data/data/com.keralatechreach.mobile_llm5/shared_prefs/`

## Dependencies

```gradle
implementation 'com.getkeepsafe.taptargetview:taptargetview:1.13.3'
```

- **Size**: ~100KB
- **License**: Apache 2.0
- **Min SDK**: 14+
- **Repository**: GitHub - KeepSafe/TapTargetView

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Tutorial not showing | Clear app data and check if views are initialized |
| Views not highlighted | Verify view IDs match in `startInteractiveTutorial()` |
| Crashes on rotation | Built-in - tutorial auto-cancels on destroy |
| Tutorial stuck | Tutorial auto-advances on tap; can cancel anytime |

## Next Steps

To add "Restart Tutorial" option in Settings:

1. Add preference in `settings.xml`:
```xml
<Preference
    android:key="restart_tutorial"
    android:title="Restart Tutorial"
    android:summary="Show the interactive tutorial again"
    android:icon="@drawable/ic_help" />
```

2. Handle click in `SettingsActivity`:
```java
findPreference("restart_tutorial").setOnPreferenceClickListener(pref -> {
    TutorialManager tutorialManager = new TutorialManager(getActivity());
    tutorialManager.resetTutorial();
    Toast.makeText(getContext(), "Tutorial will show on next launch", Toast.LENGTH_SHORT).show();
    return true;
});
```

## Support

For issues or questions:
- Check `INTERACTIVE_TUTORIAL_GUIDE.md` for detailed documentation
- Review TapTargetView GitHub: https://github.com/KeepSafe/TapTargetView
- Check logcat for tutorial-related logs with tag `TutorialManager`
