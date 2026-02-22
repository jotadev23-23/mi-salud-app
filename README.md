# 💊 Mi Salud — App para Adultos Mayores

Aplicación Android para el manejo del historial clínico personal, recordatorios de medicamentos, signos vitales y más.

---

## 📱 Características

- 🏠 **Inicio** — Resumen diario con signos vitales, medicamentos e hidratación
- 💊 **Medicamentos** — Recordatorios con alarma sonora (suave o intensa)
- ❤️ **Signos Vitales** — Presión, frecuencia, temperatura, saturación, glucosa con gráficos
- 🏥 **Historia Clínica** — Consultas, enfermedades, alergias, vacunas y dieta
- 💾 **Respaldo** — Exportar e importar todos los datos en formato JSON
- 🆘 **Emergencias** — Botón directo a 911, médico y familiar
- 🔒 **PIN de seguridad** — Protección con código de 4 dígitos
- 📸 **Fotos de recetas** — Capturá y guardá tus documentos médicos
- 🗺️ **Mapa** — Farmacia y hospital más cercano

---

## 🚀 Cómo subir a GitHub y compilar el APK

### Paso 1 — Descargar el gradle-wrapper.jar

> **Este archivo es obligatorio y no se incluye en el repositorio por su tamaño.**

Descargalo desde este enlace y colocalo en `gradle/wrapper/`:

```
https://github.com/gradle/gradle/releases/download/v8.2.0/gradle-8.2.0-bin.zip
```

O más fácil: abrí el proyecto en **Android Studio** y él lo descarga automáticamente.

### Paso 2 — Subir a GitHub

```bash
# 1. Inicializar repositorio
git init
git add .
git commit -m "Initial commit - Mi Salud v3.0"

# 2. Crear repositorio en github.com (botón "New repository")
#    Nombre sugerido: mi-salud-app

# 3. Conectar y subir
git remote add origin https://github.com/TU_USUARIO/mi-salud-app.git
git branch -M main
git push -u origin main
```

### Paso 3 — Compilar el APK automáticamente con GitHub Actions

Una vez subido el código, GitHub **compila el APK automáticamente** en la nube:

1. Entrá a tu repositorio en **github.com**
2. Clic en la pestaña **"Actions"**
3. Esperá que termine el workflow **"Build APK"** (tarda ~3-5 minutos)
4. Clic en el workflow completado → **"MiSalud-debug-APK"** → **Download**
5. Descomprimí el `.zip` → tenés tu `app-debug.apk`

### Paso 4 — Instalar el APK en el celular

1. Pasá el APK al celular (WhatsApp, cable USB, Google Drive)
2. En Android: **Ajustes → Seguridad → Fuentes desconocidas** → Activar
3. Tocá el archivo `.apk` → Instalar
4. ✅ **¡Mi Salud aparece en tu pantalla de inicio!**

---

## 🛠️ Compilar localmente (opcional)

### Requisitos
- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17
- Android SDK 34

### Pasos
```bash
# Clonar el repositorio
git clone https://github.com/TU_USUARIO/mi-salud-app.git
cd mi-salud-app

# Compilar APK debug
./gradlew assembleDebug

# El APK se genera en:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 Estructura del Proyecto

```
MiSaludApp/
├── .github/
│   └── workflows/
│       └── build.yml          ← Auto-compila APK en GitHub
├── app/
│   └── src/main/
│       ├── assets/
│       │   └── index.html     ← ⭐ LA APP COMPLETA (HTML/CSS/JS)
│       ├── java/com/misalud/app/
│       │   ├── MainActivity.java    ← WebView principal
│       │   ├── SplashActivity.java  ← Splash screen
│       │   └── BootReceiver.java    ← Receptor de arranque
│       └── res/               ← Íconos, layouts, colores
├── gradle/wrapper/
├── build.gradle
├── settings.gradle
└── gradlew
```

---

## 💾 Sobre el almacenamiento de datos

Todos los datos se guardan en el **localStorage del WebView** del dispositivo:

- 📍 Ubicación: `/data/data/com.misalud.app/app_webview/Local Storage/`
- 🔒 Solo accesible por esta app
- ✅ Persisten al cerrar y reiniciar el teléfono
- ✅ Persisten al actualizar la app
- ⚠️ Se borran al desinstalar → **usá la función de Exportar antes**

---

## 📄 Licencia

Uso personal. Libre para modificar y distribuir con fines no comerciales.
