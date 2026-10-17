# SOT Battery Tracker - Proyecto Android Nativo + WebView

Este proyecto convierte el prototipo en una aplicación Android real que lee:
1. El evento real de desconexión del cargador (`ACTION_POWER_DISCONNECTED`) y el porcentaje de batería inicial (`BatteryManager`).
2. Las horas exactas de pantalla encendida (`SCREEN_INTERACTIVE` / `SCREEN_NON_INTERACTIVE`) y uso por aplicación desde ese instante mediante `UsageStatsManager`.

## Opción A: Compilar la APK manualmente en tu PC con Android Studio
1. Descomprime este archivo ZIP en una carpeta.
2. Abre **Android Studio** -> **Open** y selecciona la carpeta descomprimida.
3. Espera a que termine el *Gradle Sync*.
4. Ve al menú superior: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. La APK se generará en `app/build/outputs/apk/debug/app-debug.apk`. Pásala a tu dispositivo Android e instálala.

## Opción B: Compilar la APK en la nube (Sin instalar Android Studio)
1. Crea un repositorio gratuito en GitHub y sube el contenido de este ZIP.
2. Ve a la pestaña **Actions** en tu repositorio y ejecuta el flujo **Build Android APK**.
3. En 2 minutos podrás descargar el archivo `app-debug.apk` listo para instalar en tu móvil o tablet.
