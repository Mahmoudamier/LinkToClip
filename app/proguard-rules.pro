# NewPipeExtractor discovers services and parsers reflectively.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# NewPipeExtractor uses Rhino for JavaScript based signature deciphering.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# Optional desktop APIs referenced by Rhino/jsoup that are absent on Android.
-dontwarn com.google.re2j.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# yt-dlp Android runtime
-keep class com.farimarwat.** { *; }
-keep class com.yausername.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn com.fasterxml.jackson.databind.exc.**
