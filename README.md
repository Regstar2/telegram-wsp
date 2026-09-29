<div align="center">

# Telegram-WSP

Неофициальный форк Telegram для Android со встроенным TgWsProxy: прокси работает внутри клиента и не требует отдельного приложения или собственного VPS. Проект сохраняет Telegram максимально близким к upstream и не изменяет `TMessagesProj/jni/tgnet/`.

[![Release](https://img.shields.io/github/v/release/Regstar2/telegram-wsp?display_name=tag&sort=semver&style=for-the-badge&logo=github&label=release)](../../releases)
[![CI](https://img.shields.io/github/actions/workflow/status/Regstar2/telegram-wsp/trusted-ci.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=CI)](../../actions/workflows/trusted-ci.yml)
[![Platform](https://img.shields.io/badge/platform-Android%205.0%2B-0A7EA4?style=for-the-badge)](#требования)
[![License](https://img.shields.io/github/license/Regstar2/telegram-wsp?style=for-the-badge&label=license)](LICENSE)

[Быстрый старт](#быстрый-старт) ·
[Документация](#документация) ·
[Релизы](../../releases) ·
[Обратная связь](../../issues)

</div>

---

## О проекте

Telegram-WSP встраивает TgWsProxy непосредственно в Telegram Android. Приложение поднимает локальный proxy runtime на `127.0.0.1:1443` и подключает его через штатный proxy API Telegram.

Цель проекта — дать обычный Telegram-клиент с WebSocket/Cloudflare-маршрутами и резервными вариантами соединения без отдельного TgWsProxy-приложения и без переноса собственного transport-кода в Telegram networking.

Проект построен как воспроизводимый overlay поверх [DrKLO/Telegram](https://github.com/DrKLO/Telegram). Полный исходный код Telegram в репозитории не хранится: нужный upstream checkout загружается по закреплённому commit.

### Принципы интеграции

Главный принцип Telegram-WSP — **минимально изменять оригинальный Telegram**. Интеграция вынесена в отдельный overlay, не меняет `TMessagesProj/jni/tgnet/` и на текущем pinned upstream затрагивает только ограниченный набор source-level путей.

Telegram-WSP использует собственные название, package ID, launcher icon и release-подпись, чтобы не выдавать себя за официальный клиент. Проект является независимым неофициальным форком Telegram for Android.

## Статус проекта

Стадия: **MVP**.

Текущий публичный релиз: **[v12.10.5-wsp.2](../../releases/tag/v12.10.5-wsp.2)**.

| Компонент | Текущее состояние |
|---|---|
| Telegram upstream | 12.10.5, build 7105 |
| Telegram commit | `dc780e81ed1261c369c27870e8e0999a1eb0b600` |
| tgwsproxy-core commit | `8016d1b56210e8edc69da9d637416b70b30971a8` |
| Android package | `org.telegram.messenger.web` |
| Встроенный TgWsProxy | Работает через локальный listener |
| Экран управления прокси | Встроен в настройки прокси Telegram |
| Подписанный APK | Публикуется через GitHub Releases |
| Автообновление приложения | Реализовано через GitHub Releases |
| Upstream sync | Автоматическая проверка DrKLO/Telegram с integration gates |

Релиз `v12.10.5-wsp.2` включает новый экран управления маршрутами, поддержку Cloudflare-доменов, WARP/AmneziaWG-профиля и Worker pools. Production APK публикуется вместе с контрольными суммами и точными исходными компонентами сборки.

## Возможности

- встроенный TgWsProxy runtime без отдельного Android-приложения;
- локальный MTProto proxy на `127.0.0.1:1443`;
- использование штатного `ConnectionsManager.setProxySettings(...)` без патчей Telegram `tgnet`;
- режимы маршрутизации **Auto / Cloudflare Proxy / WARP-AmneziaWG / Cloudflare Worker / Direct**;
- разные цепочки Auto для Wi-Fi и мобильной сети;
- ввод, обновление и проверка списка Cloudflare-доменов;
- создание, импорт, экспорт и удаление WARP/AmneziaWG-профиля;
- независимые списки Worker для Proxy Worker и Amnezia provisioning Worker;
- локализация встроенного интерфейса для локалей текущей версии Telegram;
- собственные название, launcher icon, package ID и постоянная release-подпись;
- встроенная проверка обновлений через GitHub Releases;
- проверка скачанного APK по SHA-256, package ID и signing certificate;
- автоматическая проверка новых Telegram version/build с валидацией integration overlay;
- публикация Corresponding Source для каждого публичного APK.

## Быстрый старт

1. Скачайте **[последний Telegram-WSP-release.apk](https://github.com/Regstar2/telegram-wsp/releases/latest/download/Telegram-WSP-release.apk)**.
2. Разрешите Android устанавливать приложения из выбранного источника, если система запросит это.
3. Установите APK и запустите Telegram-WSP.
4. Войдите в Telegram как в обычном клиенте.
5. При необходимости откройте штатные настройки прокси Telegram и выберите режим Telegram-WSP.

Для базового сценария отдельное приложение TgWsProxy на устройстве не требуется.

## Требования

Для готового APK:

- Android **5.0 / API 21** или новее;
- доступ в интернет;
- разрешение Android на установку APK не из магазина.

Для сборки из исходников используются JDK 17, Go 1.25.x, Android SDK, NDK 27.2.12479018 и Gradle. Точные production-настройки зафиксированы в [release.yml](.github/workflows/release.yml).

## Установка

Актуальный APK публикуется в [GitHub Releases](../../releases).

Имя пакета:

```text
org.telegram.messenger.web
```

Telegram-WSP использует постоянный release key. Новая версия устанавливается поверх предыдущей Telegram-WSP-сборки только при совпадении package ID и сертификата подписи.

Если на устройстве уже установлено приложение с тем же package ID, но другой подписью, Android не позволит обновить его поверх существующей установки.

## Использование

После запуска Telegram-WSP:

1. `TgWsProxyBootstrap` запускает `tgwsproxy-core`;
2. core открывает локальный listener `127.0.0.1:1443`;
3. Telegram получает эту конфигурацию через штатный proxy API;
4. трафик проходит через выбранный маршрут Telegram-WSP.

Если встроенный runtime не запускается, Telegram-WSP не должен произвольно сбрасывать стороннюю proxy-конфигурацию пользователя.

Настройки Cloudflare, WARP/AmneziaWG и Worker pools доступны из встроенного экрана управления прокси.

## Режимы работы

### Auto

Telegram-WSP выбирает цепочку автоматически в зависимости от типа сети.

**Wi-Fi:**

```text
Cloudflare Proxy → WARP-AmneziaWG → Cloudflare Worker → Direct
```

**Мобильная сеть:**

```text
Cloudflare Proxy → WARP-AmneziaWG → Cloudflare Worker
```

Direct intentionally не входит в мобильную Auto-цепочку.

### Cloudflare Proxy

Использует настроенный список Cloudflare-доменов. Экран управления позволяет изменить список, подтянуть актуальные домены и проверить их доступность.

### WARP-AmneziaWG

Использует WARP/AmneziaWG-профиль. Профиль можно создать, импортировать, экспортировать или удалить из интерфейса Telegram-WSP.

### Cloudflare Worker

Использует отдельный список Proxy Worker. Список Amnezia provisioning Worker хранится независимо и применяется для соответствующего provisioning-сценария.

### Direct

Пытается использовать прямой маршрут без Cloudflare Proxy, AWG и Worker. В Auto этот fallback доступен только для Wi-Fi.

## Сеть и прокси

Базовый путь соединения:

```text
Telegram-WSP
     │
     │ штатный Telegram proxy API
     ▼
127.0.0.1:1443
     │
     ▼
tgwsproxy-core
     │
     ├─ Cloudflare Proxy
     ├─ WARP / AmneziaWG
     ├─ Cloudflare Worker
     └─ Direct
```

Порядок маршрутов задаётся выбранным режимом. Telegram networking видит только локальный proxy endpoint и не содержит реализации transport Telegram-WSP.

Такой подход уменьшает собственный diff и снижает количество конфликтов при обновлении Telegram upstream.

## Архитектура

Проект разделён на три уровня:

```text
DrKLO/Telegram
      │
      │ deterministic overlay
      ▼
Telegram integration adapter
      │
      ▼
tgwsproxy-core
      │
      ▼
libtgwsproxy.so
```

Исходный Telegram checkout загружается по точному commit из [config/upstream.json](config/upstream.json), а `tgwsproxy-core` — из [config/core.json](config/core.json). Generated worktree создаётся в `.work/` и не коммитится.

Подробности: [docs/architecture.md](docs/architecture.md) и [integration/README.md](integration/README.md).

## Безопасность

Публичный APK подписывается постоянным Telegram-WSP release key.

Встроенный updater принимает новую сборку только после проверки:

- HTTPS URL из разрешённого GitHub Releases path;
- SHA-256 APK;
- package ID `org.telegram.messenger.web`;
- того же signing certificate, что у установленного приложения.

Финальную установку выполняет системный Android package installer, поэтому подтверждение пользователя остаётся обязательным.

Release key и Telegram API credentials не хранятся в репозитории. Для GitHub Actions они передаются через repository secrets.

## Обновление

Telegram-WSP проверяет:

```text
https://github.com/Regstar2/telegram-wsp/releases/latest/download/latest.json
```

не чаще одного раза в 12 часов.

Если `versionCode` новой сборки выше установленного, приложение предлагает обновление. После согласия APK скачивается, проверяется и передаётся системному installer.

Нумерация Telegram-WSP учитывает как Telegram build, так и WSP revision:

```text
versionCode = telegramBuild * 1000 + wspRevision * 10 + 9
```

Поэтому WSP hotfix той же версии Telegram может обновляться поверх предыдущего WSP-релиза.

### Автоматическое обновление Telegram upstream

Обновление состоит из двух независимых уровней:

1. **DrKLO/Telegram → Telegram-WSP.** Workflow [upstream-sync.yml](.github/workflows/upstream-sync.yml) проверяет официальный upstream, обновляет pinned commit, воспроизводит integration overlay и запускает project gates.
2. **Telegram-WSP → устройство.** После успешной проверки совместимости [release.yml](.github/workflows/release.yml) собирает подписанный APK, формирует metadata и Corresponding Source и публикует GitHub Release. Установленное приложение обнаруживает релиз через `latest.json`.

Если overlay не применяется или проверки не проходят, новая версия Telegram не должна становиться публичным Telegram-WSP-релизом.

## Разработка

Рабочий Telegram checkout и core создаются локально:

```powershell
git clone https://github.com/Regstar2/telegram-wsp.git
cd telegram-wsp

./scripts/prepare-integration.ps1 -Force
./scripts/ci.ps1
```

Основные конфигурации:

- [config/upstream.json](config/upstream.json) — pinned Telegram commit и version/build;
- [config/core.json](config/core.json) — pinned `tgwsproxy-core`;
- [integration/](integration/) — integration/branding layer;
- [scripts/](scripts/) — fetch/build/release операции.

Для локальной сборки с собственными Telegram `api_id` / `api_hash` используйте environment variables `TELEGRAM_API_ID` и `TELEGRAM_API_HASH` либо локальный `.work/telegram/local.properties`. Credentials нельзя коммитить или публиковать в Issue/PR.

## Сборка

Быстрый ARM64 prototype APK:

```powershell
./scripts/build-apk.ps1
```

Полная `afatStandalone` сборка:

```powershell
./scripts/build-apk.ps1 -Full
```

Подписанный release APK:

```powershell
./scripts/build-release.ps1
```

Результат release-сборки:

```text
dist/Telegram-WSP-release.apk
```

Создание release key — одноразовая операция:

```powershell
./scripts/create-release-keystore.ps1
```

Потеря или замена этого ключа нарушит update compatibility уже установленных сборок.

Production releases собираются на GitHub-hosted Windows runner через [release.yml](.github/workflows/release.yml).

## Тестирование

Базовые проверки репозитория:

```powershell
./scripts/ci.ps1
```

Workflow [trusted-ci.yml](.github/workflows/trusted-ci.yml) на pull request:

- выполняет repository checks;
- воспроизводит Telegram overlay с чистого pinned upstream;
- повторно запускает проверки на подготовленном worktree.

Перед публичным релизом production workflow дополнительно выполняет подписанную сборку и проверяет package, branding и APK signature.

## Документация

- [Архитектура](docs/architecture.md) — границы Telegram/core и модель минимального diff;
- [Integration layer](integration/README.md) — startup path, credentials, branding и build variants;
- [MVP scope](docs/product/mvp-scope.md) — границы текущей стадии проекта;
- [Лицензирование](docs/licensing.md) — GPL-модель, third-party компоненты и Corresponding Source;
- [NOTICE.md](NOTICE.md) — third-party notices.

Точные исходные компоненты каждой публичной сборки прикладываются к GitHub Release вместе с `SOURCE_MANIFEST.json` и `SHA256SUMS.txt`.

## Обратная связь

Ошибки и проблемы совместимости можно сообщать через [GitHub Issues](../../issues).

Для отчёта об ошибке полезно указать:

- модель устройства и версию Android;
- версию Telegram-WSP;
- выбранный режим маршрутизации;
- что ожидалось и что произошло;
- воспроизводится ли проблема с другим режимом.

Не публикуйте Telegram API credentials, signing keys, access tokens и другие секреты.

## Происхождение и благодарности

Telegram-WSP основан на:

- [DrKLO/Telegram](https://github.com/DrKLO/Telegram) — официальный исходный код Telegram for Android;
- [Regstar2/tgwsproxy-core](https://github.com/Regstar2/tgwsproxy-core) — переиспользуемое Android/native ядро;
- [Regstar2/tg-ws-proxy-android](https://github.com/Regstar2/tg-ws-proxy-android) — форк Android-обёртки TgWsProxy и источник ранней Android-интеграции;
- [amurcanov/tg-ws-proxy-android](https://github.com/amurcanov/tg-ws-proxy-android) — исходная Android-обёртка, от которой создан форк;
- [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) — первоначальный TgWsProxy/WebSocket runtime.

Происхождение proxy runtime:

```text
Flowseal/tg-ws-proxy
        ↓
amurcanov/tg-ws-proxy-android
        ↓
Regstar2/tg-ws-proxy-android
        ↓
Regstar2/tgwsproxy-core
        ↓
Telegram-WSP
```

Telegram-WSP является независимым неофициальным форком и не связан с Telegram и не одобрен Telegram.

## Ограничения

- проект находится на стадии MVP;
- поддерживается только Android;
- распространение выполняется через GitHub Releases, а не Google Play или RuStore;
- доступность конкретного маршрута зависит от сети, Cloudflare-доменов, Worker и WARP/AmneziaWG-конфигурации;
- Direct не используется как fallback мобильного Auto-режима;
- обновления требуют системного подтверждения установки Android;
- совместимость с новой версией Telegram принимается только после успешного применения overlay и CI/release gates;
- проект не изменяет и не открывает платные функции Telegram.

## Лицензия

Собственный integration/overlay и TgWsProxy-derived combined code распространяются по **GNU GPL-3.0-only**.

Telegram for Android распространяется по GNU GPL v2 or later; для объединённого клиента используется совместимая GPLv3-модель. Third-party компоненты сохраняют собственные лицензии и notices.

Каждый публичный APK сопровождается точным Corresponding Source использованной сборки.

Полный текст: [LICENSE](LICENSE). Подробности: [docs/licensing.md](docs/licensing.md).
