-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
