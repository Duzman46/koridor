# R8 configuration for Koridor.
#
# Deliberately minimal. Firebase, Play Billing, Credential Manager and coroutines all ship
# their own consumer ProGuard rules inside their AARs, so repeating those here only stops
# R8 from shrinking what the libraries themselves declared safe to remove. A blanket
# `-keep class com.google.firebase.** { *; }` was measured to add ~218 KB to the download
# for no benefit, because this app never uses Firebase's reflective deserialisation: every
# payload goes through a hand-written codec (ProfileCodec, RoomCodec, OnlineBoardCodec).
#
# Verify with:  ./gradlew :app:bundleRelease
# Mapping file: app/build/outputs/mapping/release/mapping.txt (upload to Play Console)

# ---------------------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------------------
# Removes debug logging call sites entirely, so not even the message string constants are
# left in the release binary. AppLog already guards these behind BuildConfig.DEBUG; this
# makes the guarantee structural rather than conditional.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# ---------------------------------------------------------------------------------------
# Room / WorkManager
# ---------------------------------------------------------------------------------------
# WorkManager arrives transitively through play-services-ads and stores its queue in a Room
# database. Room does not construct that database directly: it looks the generated class up
# by name with Class.forName("<database>_Impl"), so R8 renaming it makes the lookup fail and
# the app dies at startup with "Failed to create an instance of androidx.work.impl.WorkDatabase"
# before onCreate ever runs. Release-only, which is why it only appeared on device.
#
# Keeping the type and its no-argument constructor is enough; the members can still shrink.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }

# Room's generated DAOs and the migration/converter classes it resolves by name.
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract *** *Dao();
}

# Generic signatures are read reflectively by Room and by the Firebase SDK; stripping them
# turns a working type lookup into a runtime failure.
-keepattributes Signature,InnerClasses,EnclosingMethod

# ---------------------------------------------------------------------------------------
# Crash readability
# ---------------------------------------------------------------------------------------
# Keeps line numbers so Play Console can deobfuscate stack traces with the mapping file,
# while the source file name is replaced so it leaks nothing about the project layout.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
