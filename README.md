# Callout

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.1.2%20%7C%2026.2-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.0.3-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Client-side Fabric mod for Minecraft that watches chat for important mentions, plays configurable audio alerts, and keeps a persistent history of mentions with nearby context.

## Русский

### Что это
Callout это клиентский Fabric-мод для Minecraft, который помогает не пропускать важные сообщения, упоминания ника и заданные ключевые слова в чате во время игры или AFK.

Он нужен в тех случаях, когда:
- вы находитесь AFK или отвлечены от окна игры;
- сообщения в быстром чате сервера легко теряются;
- требуется мгновенно реагировать на вызовы администрации, личные упоминания или специфические события;
- нужно просмотреть историю недавних пингов и узнать, что происходило в чате вокруг них.

### Что дает мод
Callout добавляет к обычному чату Minecraft:
- гибкую систему звуковых оповещений на любые выбранные слова или никнейм;
- поддержку регулярных выражений (Regex) для сложного поиска упоминаний;
- настраиваемую громкость и высоту тона (Pitch) для каждого триггера;
- удобное графическое окно истории упоминаний с подсветкой целевой строки и контекстом;
- фильтр истории по миру или серверу, чтобы быстро разобрать AFK-пинги из нужной сессии;
- сохранение истории между выходами из мира и перезапусками игры.

### Особенности
Мод включает:
- специальную очистку текста от служебных артефактов и тегов сторонних модов (например, ChatHeads);
- фильтрацию технических префиксов отправителя в одиночной игре или LAN, чтобы исключить ложные срабатывания;
- настраиваемое отображение сообщений контекста до и после целевого пинга;
- плавную навигацию с поддержкой пагинации и колесика мыши в меню истории;
- персистентное сохранение истории в файл `config/callout_history.json`;
- интеграцию с Mod Menu для удобного доступа к настройкам.

### Настройки и Управление
Открыть меню истории пингов:
- по умолчанию привязано к клавише `'` (апостроф); можно переназначить в меню управления Minecraft («Callout» → «Открыть историю пингов»).

В меню настроек (через Mod Menu) доступны:
- `Включен`: главный переключатель работы мода;
- `Учитывать регистр`: включение/выключение чувствительности к регистру букв;
- `Пинговать свои`: разрешает или запрещает звуковые пинги от собственных сообщений в чате;
- `Основной триггер (Никнейм)`: слово, звук, громкость и тон для вашего ника;
- `Дополнительные триггеры`: список независимых триггеров с добавлением, удалением и выбором режима `Text` / `Regex`;
- настройки истории: максимальное число пингов, размер контекста до/после, сохранение истории и очистка при смене мира или сервера;
- `whisperCommand` (только в `config/callout.json`): шаблон команды для клика по нику в истории, по умолчанию `/msg %s ` (где `%s` — ник).

### Установка
Для работы нужны:
- [Fabric Loader](https://fabricmc.net/use/installer/) (>= 0.19.3)
- [Fabric API](https://modrinth.com/mod/fabric-api)

Рекомендуется:
- [Mod Menu](https://modrinth.com/mod/modmenu) (для удобной настройки через интерфейс)

Важно:
- мод является полностью клиентским (Client-side);
- история автоматически сохраняется в `config/callout_history.json`.

### Совместимость
- Minecraft `26.1.2`-`26.2` (one JAR)
- Java `25`
- Fabric Loader `0.19.3+`
- Текущая версия мода в проекте: `1.0.3`

Требования для сборки:
- JDK 25

Команда сборки:
```bash
./gradlew clean build
```

Для Windows:
```bat
gradlew.bat clean build
```

Результат:
- `build/libs/*.jar`

---

## English

### What It Is
Callout is a client-side Fabric mod for Minecraft that helps you never miss important messages, nickname mentions, or custom keywords in chat while playing or AFK.

It is useful when:
- you are AFK or focused on another window;
- fast-scrolling server chat makes important mentions easy to miss;
- you need immediate audio notifications for staff callouts, personal mentions, or specific events;
- you want to review recent ping history and see the surrounding chat context.

### What It Adds
Callout extends standard Minecraft chat with:
- a flexible sound alert system for any chosen keywords or your nickname;
- support for regular expressions (Regex) for advanced pattern matching;
- customizable volume and pitch settings for each trigger;
- a sleek, dark-slate mention history GUI with highlighted ping lines and context;
- world/server filtering for quickly reviewing AFK mentions from a specific session;
- persistent history storage that survives exiting worlds and restarting the game.

### Features
The mod includes:
- automatic text sanitization to clean up third-party mod tags (e.g. ChatHeads artifacts);
- singleplayer/LAN sender prefix filtering to prevent false self-pings;
- configurable context messages before and after each targeted ping;
- smooth navigation with mouse wheel scrolling and pagination in the history screen;
- persistent JSON storage in `config/callout_history.json`;
- Mod Menu integration for seamless access to configuration.

### Controls & Settings
Open ping history menu:
- bound to `'` (apostrophe) by default; rebind it in Minecraft's Controls menu ("Callout" → "Open ping history").

Available settings in the config screen (via Mod Menu):
- `Enabled`: main toggle for mod functionality;
- `Case Sensitive`: toggles letter case matching;
- `Ping Own`: enables or disables audio alerts for your own chat messages;
- `Main Trigger (Nickname)`: custom word, sound, volume, and pitch for your username;
- `Additional Triggers`: an editable list of independent triggers with add/remove controls and configurable `Text` / `Regex` modes;
- history settings: maximum stored pings, before/after context size, persistent storage, and clearing on world/server switch;
- `whisperCommand` (`config/callout.json` only): template used when a sender name is clicked in the history screen, default `/msg %s ` (`%s` is the name).
- `separateHistoryByWorld` (`config/callout.json` only): when `true`, worlds behind one server address (minigame lobbies, etc.) keep separate history via the spawn seed; default `false`, so every world on a server shares one history and switching worlds does not split the chat.

### Installation
Required:
- [Fabric Loader](https://fabricmc.net/use/installer/) (>= 0.19.3)
- [Fabric API](https://modrinth.com/mod/fabric-api)

Recommended:
- [Mod Menu](https://modrinth.com/mod/modmenu) (for easy GUI configuration)

Important:
- this is a purely client-side mod;
- ping history is automatically saved to `config/callout_history.json`.

### Compatibility
- Minecraft `26.1.2`-`26.2` (one JAR)
- Java `25`
- Fabric Loader `0.19.3+`
- Current project mod version: `1.0.3`

### Build
Requirements:
- JDK 25

Build and dual-target verification on Windows:
```powershell
.\gradlew.bat clean build --warning-mode all
.\gradlew.bat clean build '-Pminecraft_version=26.2' --warning-mode all
.\gradlew.bat clean build --warning-mode all
```

Output:
- `build/libs/*.jar`

### Persistence and verification

Configuration, ping history, and per-session chat buffers are written through sibling temporary files and replaced atomically where the filesystem supports it. Existing files are copied to sibling `.bak` backups before replacement, and configuration/history loading can recover from them. Multiplayer scopes are keyed by server address (or dimension as a fallback); the stable spawn-info seed is appended only when `separateHistoryByWorld` is enabled, so by default every world on a server shares one history while opt-in users can still keep plugin worlds apart. A failed settings write keeps the screen open and reports the error instead of showing a false success state.

The same sources were compiled against Minecraft 26.1.2 and 26.2 on 2026-07-22. This verifies compilation and resource processing; chat delivery, sound playback, and world-switch behavior still require an in-game test.

## Credits

Developed by `LTS_Server`. Licensed under the MIT License.
