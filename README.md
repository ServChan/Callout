Client-side Fabric mod for Minecraft that watches chat for important mentions, plays configurable audio alerts, and keeps a persistent history of mentions with nearby context.

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
- сохранение истории между выходами из мира и перезапусками игры.

### Особенности
Мод включает:
- специальную очистку текста от служебных артефактов и тегов сторонних модов (например, ChatHeads);
- фильтрацию технических префиксов отправителя в одиночной игре или LAN, чтобы исключить ложные срабатывания;
- отображение 5 сообщений контекста до и 5 сообщений после целевого пинга;
- плавную навигацию с поддержкой пагинации и колесика мыши в меню истории;
- персистентное сохранение истории в файл `config/callout_history.json`;
- интеграцию с Mod Menu для удобного доступа к настройкам.

### Настройки и Управление
Открыть меню истории пингов:
- `Ctrl + K` — открыть окно истории и контекста сообщений.

В меню настроек (через Mod Menu) доступны:
- `Включен`: главный переключатель работы мода;
- `Учитывать регистр`: включение/выключение чувствительности к регистру букв;
- `Пинговать свои`: разрешает или запрещает звуковые пинги от собственных сообщений в чате;
- `Основной триггер (Никнейм)`: слово, звук, громкость и тон для вашего ника;
- `Дополнительные триггеры`: до 5 независимых триггеров с выбором режима `Text` / `Regex`.

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
- Minecraft `26.1.2`
- Java `25`
- Fabric Loader `0.19.3+`
- Текущая версия мода в проекте: `1.0.0`

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
- persistent history storage that survives exiting worlds and restarting the game.

### Features
The mod includes:
- automatic text sanitization to clean up third-party mod tags (e.g. ChatHeads artifacts);
- singleplayer/LAN sender prefix filtering to prevent false self-pings;
- 5 context messages before and 5 context messages after each targeted ping;
- smooth navigation with mouse wheel scrolling and pagination in the history screen;
- persistent JSON storage in `config/callout_history.json`;
- Mod Menu integration for seamless access to configuration.

### Controls & Settings
Open ping history menu:
- `Ctrl + K` — open mention history and chat context window.

Available settings in the config screen (via Mod Menu):
- `Enabled`: main toggle for mod functionality;
- `Case Sensitive`: toggles letter case matching;
- `Ping Own`: enables or disables audio alerts for your own chat messages;
- `Main Trigger (Nickname)`: custom word, sound, volume, and pitch for your username;
- `Additional Triggers`: up to 5 independent triggers with configurable `Text` / `Regex` modes.

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
- Minecraft `26.1.2`
- Java `25`
- Fabric Loader `0.19.3+`
- Current project mod version: `1.0.0`

### Build
Requirements:
- JDK 25

Build command:
```bash
./gradlew clean build
```

Windows:
```bat
gradlew.bat clean build
```

Output:
- `build/libs/*.jar`
