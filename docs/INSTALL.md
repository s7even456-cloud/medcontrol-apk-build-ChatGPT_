# Instalación, compilación y APK

## Requisitos

- Android Studio actualizado.
- Android SDK instalado con compileSdk compatible con el proyecto.
- JDK 17.
- Conexión a internet para que Gradle descargue dependencias la primera vez.

## Compilar desde Android Studio

1. Descomprime `MedControlApp.zip`.
2. Abre Android Studio.
3. Selecciona `Open` y elige la carpeta `MedControlApp`.
4. Espera a que Gradle sincronice.
5. Si Android Studio propone actualizar Gradle/AGP, acepta solo si mantiene compatibilidad con Kotlin y Compose.
6. Para probar en móvil conectado: `Run > Run app`.
7. Para generar APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
8. Android Studio mostrará la ruta del APK generado.

## Compilar por terminal

Si tienes Gradle instalado:

```bash
cd MedControlApp
gradle assembleDebug
```

APK resultante esperado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para release sin firmar:

```bash
gradle assembleRelease
```

## Firmar APK release

Para instalar fuera de Play Store puedes usar debug APK en pruebas. Para un APK release instalable y estable, firma con una keystore propia:

```bash
keytool -genkeypair -v -keystore medcontrol-release.keystore -alias medcontrol -keyalg RSA -keysize 2048 -validity 10000
```

Después configura `signingConfigs` en `app/build.gradle.kts` o firma manualmente con Android Studio.

## Instalar APK sin Play Store

1. Copia el APK al móvil.
2. Abre el archivo desde el gestor de archivos.
3. Android pedirá permitir instalación desde esa fuente.
4. Activa “Instalar apps desconocidas” solo para la app desde la que abres el APK.
5. Instala.
6. Desactiva de nuevo ese permiso si no lo necesitas.

## Permisos y ajustes tras instalar

1. Abre MedControl al menos una vez.
2. Permite notificaciones.
3. En Ajustes de la app, pulsa “Abrir ajustes de alarmas exactas” y permite alarmas exactas si Android lo muestra.
4. En Ajustes de batería del móvil, marca la app como “sin restricciones” o equivalente.
5. Revisa que no esté bloqueada la actividad en segundo plano.
6. En Xiaomi/MIUI/HyperOS, Samsung, Huawei u otros fabricantes, revisa ajustes adicionales de autoinicio/ahorro.

## Probar primera alarma

1. Añade un medicamento de prueba.
2. Configura una hora 3-5 minutos posterior a la hora actual.
3. Permite notificaciones.
4. Espera la alarma.
5. Confirma la toma.
6. Verifica que el stock baja 1 unidad.
7. Verifica que el historial muestra estado “Tomada”.

## Si no suena

1. Revisa permiso de notificaciones.
2. Revisa permiso de alarmas exactas.
3. Revisa batería sin restricciones.
4. Abre la app y deja que reprograme alarmas.
5. Reinicia el móvil y abre la app una vez.
6. Haz prueba con una alarma a 3-5 minutos.
