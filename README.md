# Novo Fit Club — Android admin app

Versión Android nativa (Kotlin + Jetpack Compose) de
[novofitclub-ios-admin-app](https://github.com/dionisiotaylor/novofitclub-ios-admin-app).
Usa la misma REST API de WordPress (novofitclub.com) y las mismas credenciales:
usuario de WordPress + Application Password.

## Estado

- [x] Login con Application Password (validado contra `wp/v2/users/me`, guardado cifrado)
- [x] Bloqueo con huella / PIN al abrir o volver a la app
- [x] Pestañas Ventas · Miembros · Caja · Ajustes
- [x] Ajustes (sitio, usuario, bloqueo, cerrar sesión)
- [ ] Ventas (dashboard, lista, detalle, crear/editar)
- [ ] Miembros (lista, detalle, crear/editar, ventas del miembro)
- [ ] Caja (caja chica, sobres, cierres)
- [x] Notificaciones push de ventas nuevas (FCM) — requiere configurar Firebase, ver abajo

## Compilar

Requiere JDK 17 y el Android SDK (API 35). Desde Android Studio basta con abrir
la carpeta; por línea de comandos:

```
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Todas las versiones se firman con `signing/novo-sideload.jks`, así que un APK
nuevo se instala encima del anterior sin desinstalar. Para publicar en Play
Store hay que crear una clave aparte y privada.

## Notificaciones push (Firebase)

1. En https://console.firebase.google.com crea un proyecto (o usa uno existente)
   y agrega una app **Android** con el paquete `com.novofitclub.dashboard`.
2. Descarga `google-services.json` y ponlo en `app/google-services.json`
   (está en `.gitignore`; sin ese archivo la app compila igual, solo sin push).
3. En Firebase → Configuración del proyecto → **Cuentas de servicio** →
   *Generar nueva clave privada*. Sube ese JSON al servidor como
   `keys/firebase-service-account.json`, en la misma carpeta `keys` (fuera de
   `public_html`) donde está la clave `.p8` de APNs.
4. Instala la versión del plugin `novo-push` con soporte FCM y revisa
   wp-admin → Ajustes → Novo Push: la sección "Android (Firebase)" debe decir ✅.
