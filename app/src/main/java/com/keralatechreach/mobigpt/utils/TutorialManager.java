package com.keralatechreach.mobigpt.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.keralatechreach.mobigpt.R;

/**
 * TutorialManager - Manages the interactive step-by-step tutorial for new users
 * Shows a welcome screen followed by 5 tutorial steps highlighting key UI elements
 */
public class TutorialManager {
    
    private static final String TAG = "TutorialManager";
    private static final String PREF_NAME = "tutorial_prefs";
    private static final String KEY_TUTORIAL_COMPLETED = "tutorial_completed";
    private static final String KEY_MODEL_DROPDOWN_AUTO_OPENED = "model_dropdown_auto_opened";
    
    private final Activity activity;
    private final SharedPreferences preferences;
    private TapTargetSequence tutorialSequence;
    private volatile boolean isRunning = false;
    
    public TutorialManager(Activity activity) {
        this.activity = activity;
        this.preferences = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Check if the tutorial is currently running
     */
    public boolean isTutorialRunning() {
        return isRunning;
    }
    
    /**
     * Check if the tutorial has been completed
     */
    public boolean isTutorialCompleted() {
        return preferences.getBoolean(KEY_TUTORIAL_COMPLETED, false);
    }
    
    /**
     * Mark the tutorial as completed
     */
    public void markTutorialCompleted() {
        preferences.edit().putBoolean(KEY_TUTORIAL_COMPLETED, true).apply();
    }
    
    /**
     * Check if the model dropdown has already been auto-opened once
     */
    public boolean hasAutoOpenedModelDropdown() {
        return preferences.getBoolean(KEY_MODEL_DROPDOWN_AUTO_OPENED, false);
    }
    
    /**
     * Mark that the model dropdown has been auto-opened
     */
    public void markModelDropdownAutoOpened() {
        preferences.edit().putBoolean(KEY_MODEL_DROPDOWN_AUTO_OPENED, true).apply();
    }
    
    /**
     * Reset the tutorial (for testing or user preference)
     */
    public void resetTutorial() {
        isRunning = false;
        preferences.edit()
            .putBoolean(KEY_TUTORIAL_COMPLETED, false)
            .putBoolean(KEY_MODEL_DROPDOWN_AUTO_OPENED, false)
            .apply();
    }
    
    /**
     * Show the welcome dialog with app details
     */
    public void showWelcomeDialog(Runnable onStartTutorial) {
        new AlertDialog.Builder(activity)
            .setTitle("Welcome to MobiGPT! 🎉")
            .setMessage("MobiGPT is your personal AI assistant powered by local language models.\n\n" +
                    "✨ Key Features:\n" +
                    "• Run AI models entirely on your device\n" +
                    "• Complete privacy - no data sent to servers\n" +
                    "• Multiple model support (Llama, Phi, Gemma)\n" +
                    "• Chat history and starred messages\n" +
                    "• Export conversations to PDF\n" +
                    "• Customizable themes and settings\n\n" +
                    "Let's take a quick tour to get you started!")
            .setPositiveButton("Start Tutorial", (dialog, which) -> {
                dialog.dismiss();
                if (onStartTutorial != null) {
                    onStartTutorial.run();
                }
            })
            .setNegativeButton("Skip", (dialog, which) -> {
                markTutorialCompleted();
                dialog.dismiss();
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * Start the interactive tutorial sequence
     * @param modelSpinner The model selection spinner view
     * @param downloadProgressLayout The download progress layout
     * @param editMessage The message input field
     * @param btnSend The send button
     * @param btnHamburger The hamburger menu button
     * @param toolbar The toolbar
     */
    public void startTutorial(View modelSpinner, 
                             View downloadProgressLayout,
                             View editMessage, 
                             View btnSend,
                             View btnHamburger,
                             androidx.appcompat.widget.Toolbar toolbar) {
        startTutorial(modelSpinner, downloadProgressLayout, editMessage, btnSend, btnHamburger, toolbar, null);
    }
    
    /**
     * Start the interactive tutorial sequence with completion callback
     * @param modelSpinner The model selection spinner view
     * @param downloadProgressLayout The download progress layout
     * @param editMessage The message input field
     * @param btnSend The send button
     * @param btnHamburger The hamburger menu button
     * @param toolbar The toolbar
     * @param onComplete Callback to run when tutorial completes
     */
    public void startTutorial(View modelSpinner, 
                             View downloadProgressLayout,
                             View editMessage, 
                             View btnSend,
                             View btnHamburger,
                             androidx.appcompat.widget.Toolbar toolbar,
                             Runnable onComplete) {
        
        if (activity == null || activity.isFinishing()) {
            return;
        }
        
        isRunning = true;
        tutorialSequence = new TapTargetSequence(activity);
        
        // Step 1: Model Download
        TapTarget step1 = TapTarget.forView(modelSpinner,
                "Step 1: Download AI Model",
                "First, select and download an AI model from this dropdown. " +
                "You'll see the download progress below. Wait for it to complete before chatting.")
            .outerCircleColor(R.color.tutorial_outer_circle)
            .outerCircleAlpha(0.96f)
            .targetCircleColor(android.R.color.white)
            .titleTextSize(24)
            .titleTextColor(android.R.color.white)
            .descriptionTextSize(16)
            .descriptionTextColor(android.R.color.white)
            .textTypeface(Typeface.SANS_SERIF)
            .dimColor(android.R.color.black)
            .drawShadow(true)
            .cancelable(true)
            .tintTarget(true)
            .transparentTarget(false)
            .targetRadius(60);
        
        // Step 2: Start Chatting
        TapTarget step2 = TapTarget.forView(editMessage,
                "Step 2: Start Chatting",
                "Type your message here and tap the send button to chat with the AI. " +
                "Wait for the AI to respond before sending another message.")
            .outerCircleColor(R.color.tutorial_outer_circle)
            .outerCircleAlpha(0.96f)
            .targetCircleColor(android.R.color.white)
            .titleTextSize(24)
            .titleTextColor(android.R.color.white)
            .descriptionTextSize(16)
            .descriptionTextColor(android.R.color.white)
            .textTypeface(Typeface.SANS_SERIF)
            .dimColor(android.R.color.black)
            .drawShadow(true)
            .cancelable(true)
            .tintTarget(true)
            .transparentTarget(false)
            .targetRadius(60);
        
        // Highlight send button as well
        TapTarget step2b = TapTarget.forView(btnSend,
                "Send Button",
                "Tap here to send your message to the AI assistant.")
            .outerCircleColor(R.color.tutorial_outer_circle)
            .outerCircleAlpha(0.96f)
            .targetCircleColor(android.R.color.white)
            .titleTextSize(22)
            .titleTextColor(android.R.color.white)
            .descriptionTextSize(16)
            .descriptionTextColor(android.R.color.white)
            .textTypeface(Typeface.SANS_SERIF)
            .dimColor(android.R.color.black)
            .drawShadow(true)
            .cancelable(true)
            .tintTarget(true)
            .transparentTarget(false)
            .targetRadius(50);
        
        // Step 3: Hamburger Menu (Chat History)
        TapTarget step3 = TapTarget.forView(btnHamburger,
                "Step 3: Chat History",
                "Open this menu to view all your previous conversations. " +
                "Switch between chats, create new ones, or view starred messages.")
            .outerCircleColor(R.color.tutorial_outer_circle)
            .outerCircleAlpha(0.96f)
            .targetCircleColor(android.R.color.white)
            .titleTextSize(24)
            .titleTextColor(android.R.color.white)
            .descriptionTextSize(16)
            .descriptionTextColor(android.R.color.white)
            .textTypeface(Typeface.SANS_SERIF)
            .dimColor(android.R.color.black)
            .drawShadow(true)
            .cancelable(true)
            .tintTarget(true)
            .transparentTarget(false)
            .targetRadius(50);
        
        // Step 4: Export Settings (Overflow Menu)
        TapTarget step4 = TapTarget.forToolbarOverflow(toolbar,
                "Step 4: Export Conversations",
                "Tap the overflow menu (⋮) in the top right to export your conversations " +
                "to PDF or TXT format. Perfect for keeping records!")
            .outerCircleColor(R.color.tutorial_outer_circle)
            .outerCircleAlpha(0.96f)
            .targetCircleColor(android.R.color.white)
            .titleTextSize(24)
            .titleTextColor(android.R.color.white)
            .descriptionTextSize(16)
            .descriptionTextColor(android.R.color.white)
            .textTypeface(Typeface.SANS_SERIF)
            .dimColor(android.R.color.black)
            .drawShadow(true)
            .cancelable(true)
            .tintTarget(true)
            .transparentTarget(false)
            .targetRadius(60);
        
        // Step 5: Settings (Overflow Menu)
        TapTarget step5 = TapTarget.forToolbarOverflow(toolbar,
                "Step 5: Settings & More",
                "Access settings from the overflow menu to customize themes, " +
                "model parameters, and other preferences. You're all set! 🎉")
            .outerCircleColor(R.color.tutorial_outer_circle)
            .outerCircleAlpha(0.96f)
            .targetCircleColor(android.R.color.white)
            .titleTextSize(24)
            .titleTextColor(android.R.color.white)
            .descriptionTextSize(16)
            .descriptionTextColor(android.R.color.white)
            .textTypeface(Typeface.SANS_SERIF)
            .dimColor(android.R.color.black)
            .drawShadow(true)
            .cancelable(true)
            .tintTarget(true)
            .transparentTarget(false)
            .targetRadius(60);
        
        // Build the sequence
        tutorialSequence.targets(step1, step2, step2b, step3, step4, step5);
        
        // Set listener for sequence completion
        tutorialSequence.listener(new TapTargetSequence.Listener() {
            @Override
            public void onSequenceFinish() {
                isRunning = false;
                markTutorialCompleted();
                if (onComplete != null) {
                    onComplete.run();
                }
            }
            
            @Override
            public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                // Optional: Track which step user is on
            }
            
            @Override
            public void onSequenceCanceled(TapTarget lastTarget) {
                isRunning = false;
                markTutorialCompleted();
                // Ensure onComplete runs so notification permission is requested after tutorial
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        
        // Start the sequence
        tutorialSequence.start();
    }
    
    /**
     * Show dialog when tutorial is canceled
     */
    private void showTutorialCanceledDialog() {
        new AlertDialog.Builder(activity)
            .setTitle("Tutorial Canceled")
            .setMessage("You can restart the tutorial anytime from Settings.")
            .setPositiveButton("OK", (dialog, which) -> {
                markTutorialCompleted();
                dialog.dismiss();
                if (activity instanceof com.keralatechreach.mobigpt.MainActivity) {
                    ((com.keralatechreach.mobigpt.MainActivity) activity).requestNotificationPermission();
                }
            })
            .show();
    }
    
    /**
     * Cancel the ongoing tutorial
     */
    public void cancelTutorial() {
        isRunning = false;
        if (tutorialSequence != null) {
            tutorialSequence.cancel();
        }
    }
}
