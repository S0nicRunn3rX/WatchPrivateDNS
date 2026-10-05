# Watch Private DNS — Wear OS / Material 3 Expressive

Приложение для Samsung Galaxy Watch Ultra / Wear OS, позволяющее менять системный Private DNS прямо с часов.

## Возможности версии 1.2

- Режимы Private DNS: **Отключён / Автоматически / Вручную**.
- Ручной DNS задаётся hostname DNS-over-TLS, например `dns.google`.
- Последние 8 корректных hostname сохраняются локально и показываются в разделе **«Недавние DNS»**.
- Повторный выбор сохранённого DNS выполняется одним нажатием.
- Автоматическое определение Wear OS Bluetooth sysproxy через `NetworkCapabilities.TRANSPORT_BLUETOOTH`.
- Пока часы имеют интернет-прокси через подключённый телефон, фактический Private DNS временно переводится в **«Автоматически»**.
- Выбранный пользователем режим сохраняется отдельно и автоматически восстанавливается после исчезновения Bluetooth-прокси.
- После включения ручного DNS приложение ждёт до 12 секунд системной валидации Private DNS.
- Если strict Private DNS не становится активным, приложение возвращается в **«Автоматически»**, а неудачный hostname остаётся в истории для повторной попытки.
- Фоновая политика использует `ConnectivityManager.NetworkCallback` без постоянного foreground-service и без постоянного уведомления.
- На случай выгрузки процесса есть периодическая страховочная проверка через `JobScheduler` (примерно раз в 15 минут) и восстановление после перезагрузки.
- Интерфейс: Compose for Wear OS + Wear Material 3 Expressive.

## Почему при Bluetooth включается «Автоматически»

Wear OS обычно проксирует сетевой трафик часов через телефон при наличии Bluetooth-соединения. В AOSP такой sysproxy представлен сетью с транспортом `TRANSPORT_BLUETOOTH`. Приложение отслеживает именно наличие Bluetooth-сети с capability `INTERNET`, а не просто факт подключения любых Bluetooth-наушников/аксессуаров.

## Проверка ручного DNS

После записи `private_dns_mode=hostname` приложение проверяет `LinkProperties` активной сети:

- `isPrivateDnsActive == true`;
- `privateDnsServerName` совпадает с выбранным hostname.

Если через 12 секунд strict Private DNS не активировался, приложение считает валидацию неуспешной и переключает систему на `opportunistic` (режим **«Автоматически»**).

Если в момент проверки вообще нет активной сети/LinkProperties, немедленный сброс не выполняется: проверка будет повторена следующей фоновой задачей.

## Разрешение WRITE_SECURE_SETTINGS

Android не выдаёт это разрешение обычному приложению через стандартный runtime-dialog. После установки APK его нужно предоставить один раз через ADB:

```bash
adb shell pm grant com.daniil.watchprivatedns android.permission.WRITE_SECURE_SETTINGS
```

После этого ADB для обычного использования не требуется.

## Сборка

Проект настроен на AGP 9.4.0, Gradle 9.6.0, Java 17 и Wear Compose Material 3 1.7.0.

```bash
gradle :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Также в `.github/workflows/build-apk.yml` есть CI-сборка GitHub Actions.
