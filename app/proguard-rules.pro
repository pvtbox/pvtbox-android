# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontusemixedcaseclassnames
-verbose

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class net.pvtbox.android.ui.start.SendViaPvtboxActivity
-keep public class net.pvtbox.android.ui.start.AddToPvtboxActivity

-dontwarn java.awt.datatransfer.*
-dontwarn org.apache.harmony.awt.datatransfer.*
-dontwarn org.apache.harmony.awt.*
-dontnote org.apache.http.*
-dontnote android.net.http.*
-dontwarn net.rdrei.android.dirchooser.*
-dontwarn com.google.protobuf.*
-dontnote io.realm.internal.*
-dontwarn okio.*
-dontwarn retrofit2.*
-dontwarn com.google.android.material.snackbar.*
-keep class com.testfairy.** { *; }
-dontwarn com.testfairy.**
-keepattributes Exceptions, Signature, LineNumberTable

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers,includedescriptorclasses enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep,includedescriptorclasses class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keep class org.webrtc.** { *; }

-keep class retrofit2.** { *; }
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes EnclosingMethod

-keepclasseswithmembers class * {
    @retrofit2.* <methods>;
}

-keepclasseswithmembers interface * {
    @retrofit2.* <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-keep class proto.** { *; }

-keep class sun.misc.Unsafe { *; }
-keep class net.pvtbox.android.api { *; }

-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

-dontwarn pl.tajchert.waitingdots.**
-keep public class pl.tajchert.waitingdots.** { public protected private *; }


-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *
# Enum field names are used by the integrated EnumJsonAdapter.
# Annotate enums with @JsonClass(generateAdapter = false) to use them with Moshi.
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
}
# The name of @JsonClass types is used to look up the generated adapter.
-keepnames @com.squareup.moshi.JsonClass class *
## Retain generated JsonAdapters if annotated type is retained.
-keep public class * extends com.squareup.moshi.JsonAdapter {
    <init>(...);
    <fields>;
}
