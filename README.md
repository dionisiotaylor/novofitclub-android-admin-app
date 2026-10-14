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
- [ ] Notificaciones push (FCM, requiere soporte en el plugin `novo-push`)

## Compilar

Requiere JDK 17 y el Android SDK (API 35). Desde Android Studio basta con abrir
la carpeta; por línea de comandos:

```
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
