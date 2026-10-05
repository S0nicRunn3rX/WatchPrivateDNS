<p align="center">
  <img src="app/src/main/res/drawable/ic_app_icon.png" width="160" alt="Watch Private DNS icon">
</p>

<h1 align="center">Watch Private DNS</h1>

<p align="center">Управление системным Private DNS прямо с часов Wear OS.</p>

<p align="center">
  <a href="../../actions/workflows/build-apk.yml"><img src="../../actions/workflows/build-apk.yml/badge.svg" alt="Android build"></a>
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/S0nicRunn3rX/WatchPrivateDNS" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Wear%20OS-API%2030%2B-3DDC84" alt="Wear OS API 30+">
</p>

## Возможности

- режимы Private DNS: отключён, автоматически и вручную;
- DNS-over-TLS hostname, например `dns.google`;
- история последних восьми адресов;
- проверка системной активации strict Private DNS;
- автоматический переход в режим «Автоматически» при интернет-прокси через Bluetooth-телефон;
- восстановление выбранного режима после отключения Bluetooth-прокси;
- Wear Material 3 с системной светлой/тёмной темой и динамическими цветами;
- фоновая политика без постоянного уведомления.

## Установка и разрешение

Скачайте APK со страницы [Releases](../../releases/latest), установите на часы и один раз выдайте системное разрешение через ADB:

```bash
adb install -r WatchPrivateDNS-v1.2.2-debug.apk
adb shell pm grant com.daniil.watchprivatedns android.permission.WRITE_SECURE_SETTINGS
```

После этого ADB для обычного использования не нужен.

## Сборка

Требуются JDK 17 и Android SDK 37.

```bash
./gradlew :app:assembleDebug
```

GitHub Actions автоматически собирает APK при каждом push в `main`. Изменения по версиям перечислены в [CHANGELOG.md](CHANGELOG.md).

## Важно

Приложение изменяет системные secure settings. Используйте его только на собственном устройстве и выдавайте разрешение осознанно.

## Лицензия

Исходный код предоставляется «как есть» для личного использования и доработки.
