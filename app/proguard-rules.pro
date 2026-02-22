# Reglas ProGuard para Mi Salud
# Mantener la clase principal
-keep class com.misalud.app.** { *; }

# Mantener interfaces JavaScript
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# AppCompat
-keep class androidx.appcompat.** { *; }
