# Parsers are instantiated by reflection from the MangaParserSource enum: keep them all.
-keep class org.koitharu.kotatsu.parsers.** { *; }
-keep class com.yuko.sources.** { *; }
-dontwarn org.koitharu.kotatsu.parsers.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <fields>; }
