# Callout

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.1.0-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Client-side Fabric mod that watches chat for text/regex triggers, plays configurable audio alerts, extends chat scrollback, and keeps a persistent history of mentions with nearby context.

## Русский

### Что это

`Callout` (mod id `callout`) — клиентский Fabric-мод для Minecraft 26.3, который помогает не пропускать упоминания ника и заданные ключевые слова в чате во время игры или AFK. Он полезен, когда вы отвлеклись от окна игры, сообщения в быстром чате сервера легко теряются, нужно мгновенно реагировать на вызовы администрации, или требуется просмотреть историю недавних пингов и понять контекст вокруг них.

### Возможности

- гибкая система звуковых оповещений на любые слова или ваш никнейм;
- поддержка регулярных выражений (Regex) в дополнение к обычному тексту;
- настраиваемые громкость и высота тона (Pitch) для каждого триггера;
- графическое окно истории упоминаний с подсветкой целевой строки и сообщениями контекста до/после;
- фильтр истории по миру или серверу для разбора AFK-пингов конкретной сессии;
- очистка текста от служебных артефактов и тегов сторонних модов (например, ChatHeads);
- фильтрация технических префиксов отправителя в одиночной игре и LAN, чтобы исключить ложные самопинги;
- история чата расширена со 100 до 16384 сообщений;
- плавная навигация с пагинацией и колесом мыши;
- персистентное сохранение истории между выходами из мира и перезапусками игры.

### Управление

| Действие | Клавиша по умолчанию | Переназначение |
|---|---|---|
| Открыть историю пингов | `'` (апостроф) | Options → Controls → Callout |

### Настройки

Экран настроек открывается через **Mod Menu**. Доступны: `Включен`, `Учитывать регистр`, `Пинговать свои`, основной триггер (ник: слово, звук, громкость, тон), список дополнительных триггеров с режимом `Text` / `Regex`, параметры истории (максимум пингов, размер контекста до/после, сохранение, очистка при смене мира/сервера).

Файлы: `config/callout.json` (настройки) и `config/callout_history.json` (история). Только в `config/callout.json`: `whisperCommand` — шаблон команды при клике по нику в истории (по умолчанию `/msg %s `); `separateHistoryByWorld` — при `true` миры за одним адресом сервера ведут отдельную историю по seed спавна (по умолчанию `false`). Запись атомарная, с резервными копиями `.bak`, из которых возможно восстановление.

### Установка

1. **Fabric Loader** `0.19.3+` и **Fabric API**.
2. Скопируйте `callout-1.1.0.jar` из `build/libs/` в папку `mods/`.
3. **Mod Menu** — опционально, открывает экран настроек.

**Требования:** Minecraft `26.3` · Java `25` · только клиент.

### Сборка

```powershell
.\gradlew.bat clean build
```

Готовый JAR: `build/libs/callout-1.1.0.jar`.

---

## English

### What It Is

`Callout` (mod id `callout`) is a client-side Fabric mod for Minecraft 26.3 that helps you never miss nickname mentions or custom keywords in chat while playing or AFK. It is useful when you are focused on another window, when fast-scrolling server chat buries important mentions, when you need immediate audio notifications for staff callouts, or when you want to review recent ping history and its surrounding context.

### Features

- a flexible sound alert system for any chosen keywords or your nickname;
- regular-expression (Regex) triggers in addition to plain text;
- per-trigger volume and pitch;
- a mention-history GUI with the target line highlighted and configurable before/after context messages;
- world/server history filtering for reviewing a specific AFK session;
- text sanitization that strips third-party mod tags (e.g. ChatHeads artifacts);
- singleplayer/LAN sender-prefix filtering to prevent false self-pings;
- chat scrollback extended from 100 to 16384 messages;
- smooth navigation with pagination and mouse-wheel scrolling;
- persistent history that survives exiting worlds and restarting the game.

### Controls

| Action | Default key | Rebind |
|---|---|---|
| Open ping history | `'` (apostrophe) | Options → Controls → Callout |

### Configuration

The settings screen opens through **Mod Menu**: `Enabled`, `Case Sensitive`, `Ping Own`, the main nickname trigger (word, sound, volume, pitch), an editable list of additional `Text` / `Regex` triggers, and history options (max pings, before/after context size, persistence, clear-on-switch).

Files: `config/callout.json` (settings) and `config/callout_history.json` (history). `config/callout.json` only: `whisperCommand` — template used when a name is clicked in the history screen (default `/msg %s `); `separateHistoryByWorld` — when `true`, worlds behind one server address keep separate history via the spawn seed (default `false`). Writes are atomic with `.bak` backups usable for recovery.

### Installation

1. **Fabric Loader** `0.19.3+` and **Fabric API**.
2. Copy `callout-1.1.0.jar` from `build/libs/` into `mods/`.
3. **Mod Menu** — optional, opens the settings screen.

**Requirements:** Minecraft `26.3` · Java `25` · client-only.

### Building

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/callout-1.1.0.jar`.

## Лицензия / License

[MIT](LICENSE). Developed by LTS_Server.
