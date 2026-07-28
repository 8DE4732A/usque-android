# Usque Android

Cliente de Android para el protocolo Cloudflare WARP MASQUE, construido sobre [usque](https://github.com/Diniboy1123/usque).

Soporta **Modo VPN** (tráfico global) y **Modo Proxy SOCKS5** (proxy local), y es compatible tanto con cuentas personales como con cuentas de equipo de ZeroTrust.

## Descargas

Descarga el APK más reciente desde la página de [Releases](../../releases).

## Características

- Modo VPN global (basado en Android VpnService)
- Proxy local SOCKS5 (por defecto `127.0.0.1:1080`)
- Registro automático de cuenta de Cloudflare WARP
- Soporte para cuentas de equipo de ZeroTrust (autorización completada mediante WebView)
- Reconexión automática al cambiar de red
- Soporte opcional para HTTP/2 y HTTP/3 (QUIC)
- Personalización de SNI, DNS, MTU y Endpoint

## Construcción

### Dependencias previas

- Android Studio (con JDK 17)
- Go 1.25.5+
- Android NDK (instalado a través del SDK Manager)
- gomobile

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
```

### Pasos

```bash
git clone --recurse-submodules https://github.com/8DE4732A/usque-android.git
cd usque-android

# Construir la librería nativa de Go
bash scripts/build-aar.sh

# Construir APK de Debug
./gradlew assembleDebug
```

El resultado se encuentra en `app/build/outputs/apk/debug/app-debug.apk`.

### Construcción firmada de Release

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/release.jks \
  -Pandroid.injected.signing.store.password=<store_password> \
  -Pandroid.injected.signing.key.alias=<key_alias> \
  -Pandroid.injected.signing.key.password=<key_password>
```

## Publicación

Después de pushear un tag, GitHub Actions construye y publica automáticamente el APK de Release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Es necesario configurar los siguientes secretos en **Settings → Secrets and variables → Actions** del repositorio:

| Secret | Descripción |
|--------|------|
| `KEYSTORE_BASE64` | Salida de `base64 -i release.jks` |
| `KEYSTORE_PASSWORD` | Contraseña del keystore |
| `KEY_ALIAS` | Alias de la clave (key alias) |
| `KEY_PASSWORD` | Contraseña de la clave |

## Estructura del Proyecto

```
usque-android/
├── app/                        Módulo de la aplicación Android
│   └── src/main/java/.../
│       ├── data/               Almacenamiento de configuración (EncryptedSharedPreferences)
│       ├── nativebridge/       Capa de puente Go JNI
│       ├── service/            VpnService / SocksProxyService
│       └── ui/                 UI de Compose (screens / viewmodel)
├── third_party/usque/          Librería núcleo de Go (git submodule)
│   └── mobile/                 Capa de exportación de gomobile
└── scripts/build-aar.sh        Script de construcción de AAR
```

## Descargo de Responsabilidad

Este proyecto es únicamente para fines de aprendizaje e investigación. Antes de usarlo, asegúrese de cumplir con las leyes locales y los términos de servicio de Cloudflare.
