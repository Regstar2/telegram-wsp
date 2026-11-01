# Architecture

## Цель архитектуры

Минимизировать стоимость сопровождения при обновлениях `DrKLO/Telegram`. Telegram должен оставаться upstream-owned кодовой базой, а собственная логика — жить вне неё.

## Компоненты

```text
DrKLO/Telegram (clean upstream checkout)
        |
        | minimal patch / overlay
        v
Telegram integration adapter
        |
        v
tgwsproxy-core
        |
        v
libtgwsproxy.so
        |
        +-- cf_proxy_ws
        +-- awg_warp
        +-- cf_worker_ws
        +-- direct_ws
```

## 1. Upstream Telegram

Telegram не хранится в этом репозитории. `scripts/fetch-upstream.ps1` получает точный commit из `config/upstream.json` в `.work/telegram`.

Все изменения Telegram должны быть воспроизводимы как patch/overlay. Ручное редактирование `.work/telegram` не считается долговечным состоянием проекта.

## 2. tgwsproxy-core

Целевой reusable модуль не должен зависеть от классов Telegram.

Ответственность core:

- запуск/остановка native runtime;
- listener на localhost;
- конфигурация route policy;
- статус runtime;
- минимальный lifecycle API;
- native bridge к `libtgwsproxy.so`.

Core не должен:

- импортировать Telegram UI;
- изменять Telegram SharedPreferences напрямую;
- знать о `ConnectionsManager`;
- содержать собственный Telegram fork logic.

## 3. Telegram integration adapter

Это единственный слой, которому разрешено знать одновременно о Telegram и `tgwsproxy-core`.

Минимальная ответственность:

1. запустить core;
2. получить local port/secret;
3. вызвать штатный `ConnectionsManager.setProxySettings(...)` либо эквивалентный официальный внутренний proxy path Telegram;
4. корректно остановить/перезапустить runtime по lifecycle.

Если upstream меняет proxy API, исправляться должен этот adapter, а не core.

## 4. Startup hook

Текущая интеграция добавляет startup hook в `TMessagesProj_AppStandalone/src/main/java/org/telegram/messenger/ApplicationLoaderImpl.java`. Он вызывает `TgWsProxyBootstrap`, который инициализирует `TgWsProxyController` и локальный core. UI подключён через `ProxyListActivity` и `TgWsProxySettingsActivity`.

Изменяемые Telegram source-level пути перечислены в разделе 10. Лимит — **8 путей**; `TMessagesProj/jni/tgnet/` не изменяется.

## 5. Proxy path

Telegram должен видеть обычный локальный proxy:

```text
Telegram -> 127.0.0.1:<port> -> tgwsproxy-core -> WebSocket/Cloudflare -> Telegram infrastructure
```

Telegram не обязан знать, что за localhost находится WebSocket transport.

## 6. Запрещённая зона

До отдельного архитектурного решения запрещено патчить:

```text
TMessagesProj/jni/tgnet/
```

Причина: это резко увеличивает конфликтность upstream sync и превращает проект в самостоятельную реализацию Telegram networking.

## 7. Upstream update model

Текущая модель:

```text
config/upstream.json
       |
       v
fetch exact commit
       |
       v
apply patches/overlay
       |
       v
build + smoke
```

Текущий [upstream-sync.yml](../.github/workflows/upstream-sync.yml) запускается ежедневно. Если найден новый Telegram build/commit, workflow обновляет pin, воспроизводит overlay, выполняет `scripts/ci.ps1` и компилирует prototype APK **до** фиксации нового pin в `main`. При успешной проверке workflow публикует pin и передаёт commit в [release.yml](../.github/workflows/release.yml), где отдельно собирается и публикуется подписанный APK с Corresponding Source. Если сборка публикации сорвалась после фиксации pin, следующий scheduled run может повторить выпуск. Релизы с уже существующим tag не перезаписываются.

Проверка нового upstream не означает автоматического подтверждения работы всех сетевых маршрутов на реальном устройстве.

## 8. Diff budget

Integration CI проверяет:

- отсутствие изменений `TMessagesProj/jni/tgnet/`;
- лимит в 8 изменённых upstream source-level путей;
- точное применение overlay с отказом при несовместимом SDK anchor;
- повторяемость интеграции и prototype APK на pinned upstream и Telegram 13.0.3 regression fixture.

Подписанная release-сборка и установка на устройство — отдельные проверки.

Текущий budget — не более 8 source-level upstream-путей.

## 9. Лицензии

Лицензионный аудит завершён и зафиксирован в [licensing.md](licensing.md).

Принятая модель:

- Telegram for Android используется по его официальной лицензии GNU GPL v2 or later с
  выбором GPLv3 для объединённого клиента;
- integration/overlay и TgWsProxy-derived combined code — GNU GPL-3.0-only;
- third-party компоненты сохраняют собственные совместимые лицензии и notices;
- публичный APK сопровождается точным полным Corresponding Source собранной версии.

Архитектурное следствие: runtime и integration source должны оставаться воспроизводимыми
из публичного release source bundle; бинарная `libtgwsproxy.so` без соответствующего
исходного кода не является допустимым release input.


## 10. Текущий overlay

Для Telegram 13.1.1 (7237) используется `tgwsproxy-core` из [`config/core.json`](../config/core.json). Integration layer формирует 8 source-level изменений:

```text
TMessagesProj/build.gradle
TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
TMessagesProj/src/main/java/org/telegram/messenger/TgWsProxyController.java
TMessagesProj/src/main/java/org/telegram/ui/ProxyListActivity.java
TMessagesProj/src/main/java/org/telegram/ui/TgWsProxySettingsActivity.java
TMessagesProj_AppStandalone/build.gradle
TMessagesProj_AppStandalone/src/main/java/org/telegram/messenger/ApplicationLoaderImpl.java
TMessagesProj_AppStandalone/src/main/java/org/telegram/messenger/TgWsProxyBootstrap.java
```

AAR собирается из `config/core.json` и копируется только в локальную
`.work/telegram/.tgwsproxy/`. Generated binary не входит в source diff.

`TgWsProxyBootstrap` остаётся startup/update hook, а runtime lifecycle и пользовательская конфигурация вынесены в `TgWsProxyController`.

Controller:

1. генерирует и сохраняет локальный 16-byte MTProto secret;
2. формирует route policy и запускает `TgWsProxyCore` на `127.0.0.1:1443`;
3. управляет Cloudflare domains, WARP/AmneziaWG profile и Worker pools;
4. сохраняет штатные Telegram proxy preferences;
5. вызывает `ConnectionsManager.setProxySettings(...)`;
6. при ошибке core отключает только ранее управляемый localhost proxy.

`TgWsProxySettingsActivity` — Telegram-style UI, который общается только с controller и не обращается к native/JNA напрямую.

Overlay применяется `scripts/apply-integration.ps1` через точные anchor-замены.
Если upstream изменит anchor, процесс завершается ошибкой вместо fuzzy merge.

### Надёжность runtime в WSP 3

В `v13.1.1-wsp.3` обновлён pinned core без изменения границы Telegram/core. В runtime добавлена обработка полного исчерпания Cloudflare domain pool (включая per-DC cooldown/in-flight reservation), отмена устаревших dial/TLS-операций при смене route policy и корректная привязка событий к поколению маршрута. Изменения TLS/WebSocket и Worker transport/failover относятся к core, а не к `tgnet`.

Это описание поведения кода и релиза, **не доказательство** доступности соответствующих endpoint в любой сети. См. [release notes](releases/v13.1.1-wsp.3.md).
