# Xuper Donador — Proyecto Android Nativo (Sin Bloqueos CORS)

## ¿Cómo evita los bloqueos CORS?
En lugar de usar `fetch()` desde el motor Chromium del navegador/WebView (que envía `Origin: null` y dispara preflights `OPTIONS` bloqueados por CORS), esta app intercepta todas las llamadas de red a través de un `@JavascriptInterface` llamado `AndroidNative` (`MainActivity.kt`) que ejecuta las peticiones directamente desde **`java.net.HttpURLConnection` nativo de Android**, enviando `User-Agent: Dalvik/2.1.0` y respetando el túnel VPN del teléfono.

---

## Opción 1: Compilar el `.apk` sin instalar nada en tu PC (GitHub Actions - 2 minutos)
1. Crea un repositorio nuevo (puede ser **Privado**) en [github.com/new](https://github.com/new).
2. Sube todos los archivos de esta carpeta al repositorio.
3. Ve a la pestaña **Actions** en GitHub y selecciona **Compilar APK Nativa (Xuper Donador)** (o espera a que termine la ejecución automática).
4. Al finalizar, descarga el artefacto **`XuperDonador-APK`** desde la parte inferior de la ejecución: adentro viene `app-debug.apk` listo para instalar en tu Android.

---

## Opción 2: Compilar desde Android Studio
1. Abre **Android Studio** -> **Open** y selecciona esta carpeta descomprimida.
2. Espera a que termine la sincronización de Gradle.
3. En el menú superior, ve a **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. El archivo generado quedará en `app/build/outputs/apk/debug/app-debug.apk`.
