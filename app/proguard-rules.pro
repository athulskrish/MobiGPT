# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# =============================================================================
# OBFUSCATION SETTINGS
# =============================================================================

# Enable aggressive obfuscation
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Obfuscation settings - aggressive repackaging for security
-allowaccessmodification
-repackageclasses 'o'
-flattenpackagehierarchy 'o'

# Remove source file attribute for better obfuscation
-renamesourcefileattribute SourceFile

# Aggressive optimization
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Remove debug information
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove debugging stack traces
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# =============================================================================
# ANDROID CORE COMPONENTS
# =============================================================================

# Keep only necessary lifecycle callbacks for Android components
-keep public class * extends android.app.Activity {
    public void onCreate(android.os.Bundle);
    public void onDestroy();
}
-keep public class * extends android.app.Application {
    public void onCreate();
    public void onTerminate();
}
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class com.keralatechreach.mobigpt.** extends androidx.fragment.app.Fragment {
    public void onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle);
    public void onViewCreated(android.view.View, android.os.Bundle);
}

# Keep only essential MainActivity members
-keep class com.keralatechreach.mobigpt.MainActivity {
    protected void onCreate(android.os.Bundle);
    # Keep fields that are accessed across threads to prevent NPE
    <fields>;
}

# Keep MainActivity as ChatManagerListener implementation
-keep class com.keralatechreach.mobigpt.MainActivity implements com.keralatechreach.mobigpt.ChatManager$ChatManagerListener {
    public <methods>;
}

# Keep only public constants that are accessed externally
-keep class com.keralatechreach.mobigpt.Constants {
    public static final <fields>;
}

# Keep native methods (JNI) - only the methods, not the entire class
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep constructors for classes with custom constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep only custom view constructors (needed for XML inflation)
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# =============================================================================
# DATABASE AND ROOM
# =============================================================================

# Keep only necessary Room annotations and interfaces
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * {
    <init>(...);
}
-keep @androidx.room.Dao interface * {
    *;
}

# =============================================================================
# SQLCIPHER
# =============================================================================

# CRITICAL: Keep SQLCipher classes and fields for JNI
# The native library (libsqlcipher.so) expects specific field names
-dontwarn net.sqlcipher.**
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-keepclasseswithmembernames class net.sqlcipher.** {
    native <methods>;
}

# Modern SQLCipher Android 4.10.0+ (net.zetetic)
-dontwarn net.zetetic.**
-keep class net.zetetic.** { *; }
-keep interface net.zetetic.** { *; }
-keepclasseswithmembernames class net.zetetic.** {
    native <methods>;
}
-keepclassmembers class net.zetetic.database.sqlcipher.** {
    *;
}

# Keep only database constructor and essential methods
-keep class com.keralatechreach.mobigpt.database.ChatDatabase {
    public static com.keralatechreach.mobigpt.database.ChatDatabase getDatabase(android.content.Context);
    public abstract com.keralatechreach.mobigpt.database.ChatDao chatDao();
    public abstract com.keralatechreach.mobigpt.database.MessageDao messageDao();
}

# Keep DAO methods (required by Room at runtime)
-keep,allowobfuscation interface com.keralatechreach.mobigpt.database.ChatDao
-keepclassmembers interface com.keralatechreach.mobigpt.database.ChatDao { *; }
-keep,allowobfuscation interface com.keralatechreach.mobigpt.database.MessageDao  
-keepclassmembers interface com.keralatechreach.mobigpt.database.MessageDao { *; }

# CRITICAL: Keep Chat and Message entities for Room and chat history
# These are accessed when opening old conversations and starring messages
-keep,allowobfuscation class com.keralatechreach.mobigpt.database.Chat
-keepclassmembers class com.keralatechreach.mobigpt.database.Chat { 
    <fields>; 
    <init>(...); 
    public long id;
    public java.lang.String title;
    public long createdAt;
    public long lastMessageTime;
}

-keep,allowobfuscation class com.keralatechreach.mobigpt.database.Message
-keepclassmembers class com.keralatechreach.mobigpt.database.Message { 
    <fields>; 
    <init>(...);
    public long id;
    public long chatId;
    public boolean isUser;
    public java.lang.String content;
    public long timestamp;
    public boolean isStarred;
}

# Keep TypeConverters for Room
-keep class * extends androidx.room.TypeConverter {
    public static *** to*(...);
    public static *** from*(...);
}

# =============================================================================
# KOTLIN AND COROUTINES
# =============================================================================

# Keep only essential Kotlin metadata (minimal for functionality)
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Keep only coroutine dispatcher names (obfuscate everything else)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory

# =============================================================================
# JNI AND NATIVE CODE
# =============================================================================

# Keep only JNI interface methods for AI/LLM engine
-keep class com.keralatechreach.mobigpt.ai.** {
    native <methods>;
    public <init>(...);
}

# CRITICAL: Keep LlamaEngine and all its inner classes for JNI
-keep class com.keralatechreach.mobigpt.ai.LlamaEngine { *; }
-keep class com.keralatechreach.mobigpt.ai.LlamaEngine$* { *; }
-keep class com.keralatechreach.mobigpt.ai.LlamaEngine$Companion { *; }
-keep class com.keralatechreach.mobigpt.ai.LlamaEngine$Companion$* { *; }
-keep class com.keralatechreach.mobigpt.ai.LlamaEngine$Companion$** { *; }

# Keep IntVar class specifically (accessed by JNI in native code)
-keep class com.keralatechreach.mobigpt.ai.LlamaEngine$Companion$IntVar {
    *;
}
-keepclassmembers class com.keralatechreach.mobigpt.ai.LlamaEngine$Companion$IntVar {
    *;
}

# Keep State sealed interface and all implementations
-keep interface com.keralatechreach.mobigpt.ai.LlamaEngine$Companion$State { *; }
-keep class com.keralatechreach.mobigpt.ai.LlamaEngine$Companion$State$** { *; }

# Keep specific public methods needed for JNI callbacks
-keep class com.keralatechreach.mobigpt.ModelConfig {
    public <init>(...);
    public <fields>;
}

# CRITICAL: Keep ModelDownloadManager and all pause/resume functionality
-keep class com.keralatechreach.mobigpt.ModelDownloadManager {
    <init>(...);
    <fields>;
    <methods>;
}

# Keep ModelDownloadManager.DownloadListener interface (prevents NPE on callbacks)
-keep interface com.keralatechreach.mobigpt.ModelDownloadManager$DownloadListener {
    void onDownloadStarted(...);
    void onDownloadProgress(...);
    void onDownloadCompleted(...);
    void onDownloadFailed(...);
    void onDownloadCancelled(...);
    void onDownloadPaused(...);
    void onDownloadResumed(...);
}

# Keep ModelDownloadManager inner classes for pause/resume state
-keep class com.keralatechreach.mobigpt.ModelDownloadManager$DownloadState {
    *;
}

-keep class com.keralatechreach.mobigpt.ModelDownloadManager$DownloadSession {
    <init>(...);
    <fields>;
    <methods>;
}



# Keep ModelDownloadManager.ProgressMonitorTask for download progress tracking
-keep class com.keralatechreach.mobigpt.ModelDownloadManager$ProgressMonitorTask {
    <init>(...);
    <fields>;
    <methods>;
}

# CRITICAL: Keep ChatManager and all its members to prevent NPE when accessed from MainActivity
# The class is initialized in a background thread and accessed from UI thread via spinner callbacks
-keep class com.keralatechreach.mobigpt.ChatManager {
    <init>(...);
    <fields>;
    <methods>;
}

# Keep ChatManager.ChatManagerListener interface and all implementations
-keep interface com.keralatechreach.mobigpt.ChatManager$ChatManagerListener {
    <methods>;
}

# Keep ChatManager.ModelLoadCallback interface
-keep interface com.keralatechreach.mobigpt.ChatManager$ModelLoadCallback {
    <methods>;
}

# =============================================================================
# REFLECTION AND SERIALIZATION
# =============================================================================

# Keep classes that might be accessed via reflection
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable classes
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# =============================================================================
# UTILITY AND ADAPTER CLASSES
# =============================================================================

# Obfuscate utility classes - only keep what's needed
-keep,allowobfuscation class com.keralatechreach.mobigpt.utils.** 

# CRITICAL: Keep ExportHelper and its methods for conversation export functionality
-keep class com.keralatechreach.mobigpt.utils.ExportHelper {
    public static *** exportToMarkdown(...);
    public static *** exportToPDF(...);
    public static void shareFile(...);
    public static *** getMimeType(...);
    private static *** sanitizeFilename(...);
    private static void showExportNotification(...);
    *;
}



# =============================================================================
# MEDIASTORE AND FILE EXPORT
# =============================================================================

# Keep MediaStore classes for export functionality
-keep class android.provider.MediaStore { *; }
-keep class android.provider.MediaStore$** { *; }

# Keep ContentResolver and ContentValues for file operations
-keep class android.content.ContentValues { *; }
-keep class android.content.ContentResolver { *; }

# Keep notification classes for export notifications
-keep class androidx.core.app.NotificationCompat { *; }
-keep class androidx.core.app.NotificationCompat$** { *; }
-keep class android.app.NotificationChannel { *; }
-keep class android.app.NotificationManager { *; }

# Keep only RecyclerView adapter essentials
-keep class com.keralatechreach.mobigpt.adapter.** {
    public <init>(...);
    public *** onCreateViewHolder(...);
    public void onBindViewHolder(...);
    public int getItemCount();
}

# Keep ViewHolder inner classes
-keepclassmembers class com.keralatechreach.mobigpt.adapter.**$* {
    public <init>(...);
}

# Keep adapter interfaces used for callbacks (prevent NPE from obfuscated interface methods)
-keep interface com.keralatechreach.mobigpt.adapter.**$On*Listener {
    <methods>;
}

# CRITICAL: Keep ChatHistoryAdapter and its click listener interface
# This is needed to open old chat conversations from history
-keep class com.keralatechreach.mobigpt.adapter.ChatHistoryAdapter {
    <init>(...);
    <methods>;
}

-keep interface com.keralatechreach.mobigpt.adapter.ChatHistoryAdapter$OnChatClickListener {
    <methods>;
}

# Keep ChatHistoryAdapter.ChatHistoryViewHolder and its bind method
-keep class com.keralatechreach.mobigpt.adapter.ChatHistoryAdapter$* {
    <init>(...);
    <fields>;
    <methods>;
}

# CRITICAL: Keep MessageAdapter and its listener interface
# This handles context menu actions: copy, star, regenerate, retry, share, multi-select
-keep class com.keralatechreach.mobigpt.adapter.MessageAdapter {
    <init>(...);
    <fields>;
    <methods>;
}

# Keep MessageAdapter ViewHolder classes to prevent star indicator issues
-keep class com.keralatechreach.mobigpt.adapter.MessageAdapter$UserMessageViewHolder {
    <init>(...);
    <fields>;
    void bind(...);
}

-keep class com.keralatechreach.mobigpt.adapter.MessageAdapter$AIMessageViewHolder {
    <init>(...);
    <fields>;
    void bind(...);
}

-keep interface com.keralatechreach.mobigpt.adapter.MessageAdapter$OnMessageActionListener {
    void onMessageCopied(...);
    void onMessageStarred(...);
    void onMessageRegenerated(...);
    void onMessageRetried(...);
    void onMessageShared(...);
    void onMultipleMessagesShared(...);
    void onSelectionModeChanged(...);
}



# =============================================================================
# UI COMPONENTS AND ACTIVITIES
# =============================================================================

# CRITICAL: Keep all Activity classes - needed for app navigation
-keep class com.keralatechreach.mobigpt.StarredMessagesActivity {
    <init>(...);
    <methods>;
}

-keep class com.keralatechreach.mobigpt.OnboardingActivity {
    <init>(...);
    <methods>;
    public static boolean isOnboardingCompleted(...);
    public static void resetOnboarding(...);
}

# Keep BaseActivity and lifecycle management
-keep class com.keralatechreach.mobigpt.base.BaseActivity {
    <init>(...);
    protected <methods>;
}



# =============================================================================
# SECURITY AND NETWORK COMPONENTS
# =============================================================================

# CRITICAL: Keep SecurePreferences for encrypted storage
-keep class com.keralatechreach.mobigpt.security.SecurePreferences {
    <init>(...);
    public <methods>;
    public static *** createWithMigration(...);
}

# CRITICAL: Keep network security components - prevents MITM attacks
-keep class com.keralatechreach.mobigpt.utils.SecureNetworkManager {
    public static *** createSecureConnection(...);
    public static *** createConnection(...);
    public static boolean isSecureUrl(...);
    public static void setTimeouts(...);
    public static void configureResume(...);
    <methods>;
}







# =============================================================================
# AI AND THREADING COMPONENTS
# =============================================================================

# CRITICAL: Keep MobiGPTAI and its components
-keep class com.keralatechreach.mobigpt.ai.MobiGPTAI** { *; }



# =============================================================================
# ADAPTER CLASSES - ADDITIONAL
# =============================================================================

# Keep OnboardingPagerAdapter for first-time user experience
-keep class com.keralatechreach.mobigpt.adapter.OnboardingPagerAdapter {
    <init>(...);
    <methods>;
}

-keep class com.keralatechreach.mobigpt.adapter.OnboardingPagerAdapter$OnboardingViewHolder {
    <init>(...);
    public void bind(...);
}

-keep class com.keralatechreach.mobigpt.adapter.OnboardingPagerAdapter$OnboardingPage {
    <init>(...);
    <fields>;
}

# =============================================================================
# UTILITY CLASSES - ADDITIONAL
# =============================================================================

# Keep NetworkUtils for connectivity checks
-keep class com.keralatechreach.mobigpt.utils.NetworkUtils {
    public static <methods>;
}



# Keep MarkdownFormatter for message rendering
-keep class com.keralatechreach.mobigpt.utils.MarkdownFormatter {
    public static <methods>;
}

# Keep MessageSwipeCallback for swipe actions
-keep class com.keralatechreach.mobigpt.utils.MessageSwipeCallback {
    <init>(...);
    <methods>;
}


# =============================================================================
# ANDROIDX SECURITY CRYPTO
# =============================================================================

# CRITICAL: Keep EncryptedSharedPreferences and MasterKey for secure storage
-keep class androidx.security.crypto.EncryptedSharedPreferences {
    public static *** create(...);
    <methods>;
}

-keep class androidx.security.crypto.MasterKey {
    <init>(...);
    <methods>;
}

-keep class androidx.security.crypto.MasterKey$Builder {
    <init>(...);
    <methods>;
}

# Keep encryption schemes
-keep class androidx.security.crypto.EncryptedSharedPreferences$PrefKeyEncryptionScheme {
    *;
}

-keep class androidx.security.crypto.EncryptedSharedPreferences$PrefValueEncryptionScheme {
    *;
}

# Keep MasterKey KeyScheme
-keep class androidx.security.crypto.MasterKey$KeyScheme {
    *;
}

# =============================================================================
# CONNECTIVITY AND NETWORK CALLBACKS
# =============================================================================

# Keep ConnectivityManager.NetworkCallback for network monitoring
-keep class android.net.ConnectivityManager$NetworkCallback {
    <methods>;
}

-keep class android.net.NetworkCapabilities {
    public boolean hasTransport(...);
    public boolean hasCapability(...);
}

-keep class android.net.NetworkRequest {
    <methods>;
}

-keep class android.net.NetworkRequest$Builder {
    <init>(...);
    <methods>;
}

# =============================================================================
# TESTING EXCLUSIONS
# =============================================================================

# Remove test classes completely from release builds
-assumenosideeffects class com.keralatechreach.mobigpt.test.** {
    *;
}

# =============================================================================
# OPTIMIZATION SETTINGS
# Keep crash line numbers and essential attributes
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes Exceptions,InnerClasses,EnclosingMethod

# =============================================================================
# TINK / SECURITY CRYPTO
# =============================================================================

# Keep Tink classes needed by security-crypto
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-keep class com.google.crypto.tink.** { *; }

# =============================================================================
# SLF4J LOGGING
# =============================================================================

# Suppress warnings for missing SLF4J implementation (not needed on Android)
-dontwarn org.slf4j.impl.StaticLoggerBinder

# JSR305 annotations
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# Ignore missing Google API client classes (not used in Android security-crypto)
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# Keep KeysDownloader but don't fail on missing dependencies
-dontwarn com.google.crypto.tink.util.KeysDownloader
-dontwarn com.google.crypto.tink.util.KeysDownloader$Builder

# =============================================================================
# ADDITIONAL SECURITY
# =============================================================================

# Remove stack traces from exceptions in production
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
    public *** getStackTrace();
}

# Remove toString() methods to prevent information leakage
-assumenosideeffects class * {
    public java.lang.String toString();
}

# Aggressive obfuscation dictionaries
-classobfuscationdictionary proguard-class-dictionary.txt
-packageobfuscationdictionary proguard-package-dictionary.txt
-obfuscationdictionary proguard-obfuscation-dictionary.txt

# Remove metadata that could expose code structure
-adaptresourcefilenames **.properties
-adaptresourcefilecontents **.properties,META-INF/MANIFEST.MF

# Optimize and minimize class structure
-mergeinterfacesaggressively
-overloadaggressively