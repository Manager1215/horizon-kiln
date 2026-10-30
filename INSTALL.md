# Установка horizon-kiln 0.1.0-alpha · Installing

[English below](#english)

Альфа: проверена на macOS (Apple Silicon) с Horizon Nightly 2026-10-29 · 5e391eb. На Windows и Linux не
проверялась.

## Что внутри архива

| | |
|---|---|
| `kiln-native/` | нативный мод Kiln (распакованный `.hnmod`): лаунчер встраивает его в игру |
| `kiln-0.1.0.hnmod` | он же одним файлом |
| `kiln/` | папка для игры: `kiln-runtime.jar` (Java-часть Kiln) и `mods/kiln-timer-0.1.0-alpha.jar` (таймер) |
| `kiln-timer-0.1.0-alpha.jar` (отдельно в релизе) | тот же таймер для обычного Minecraft 26.3 с Fabric |

## Horizon + Kiln + таймер

Нужны: лаунчер Horizon с подготовленной 26.3 и Java 25 (Kiln найдёт её сам: `JAVA_HOME`, Java из лаунчера
Minecraft, установленные JDK; иначе поставьте, например, Temurin 25).

1. **Прочитайте код Kiln.** Нативные моды работают без песочницы, с вашими правами; лаунчер собирает их только
   после вашего явного согласия. Исходники — в `kiln-native/mixins/kiln/` (и в репозитории).
2. **Встройте Kiln в игру** (Терминал, путь — к распакованному архиву):
   ```sh
   /Applications/Horizon.app/Contents/MacOS/horizon-launcher native-build ~/Downloads/horizon-kiln-0.1.0-alpha/kiln-native --trust-native-code
   ```
   Первый раз лаунчер скачает свой Rust (~450 МБ) и соберёт игру (~10 минут); дальше — минуты. Игра с Kiln
   появится в лаунчере как инстанс **Native mods** — со своими мирами и настройками.
3. **Запустите Native mods** и войдите в любой мир. Kiln напишет в чат, где его папка:
   `Kiln: no Java mods yet. Put .jar mods in …/kiln/mods and kiln-runtime.jar in …/kiln`. Выйдите из игры.
4. **Скопируйте** содержимое папки `kiln/` из архива в эту папку `…/kiln` (`kiln-runtime.jar` и `mods/`).
5. **Играйте**: в новом мире таймер стартует сам. В Options > Mods Kiln виден строкой «Kiln 0.1.0».

### Таймер
- Клавиши: **J** — старт / сплит, **K** — сброс, **U** — скрыть панель.
- Сплиты: Nether, Bastion, Fortress, Blind, Stronghold, End, Finish — сами, по достижениям и измерениям.
- Настройки: `…/kiln/config/kiln_timer/settings.properties`
  (`corner=top_left|top_right|bottom_left|bottom_right`, `finish=dragon|leave_end|manual`, `shown=true|false`,
  `chat_splits=true|false`). Рекорды — `records.txt` рядом.
- Только одиночная игра. Не одобрен speedrun.com.

### Если что-то не так
- «Kiln: no Java 25 or newer found» — Kiln не нашёл Java, на которой работают моды (таймеру нужна 25).
  Поставьте Java 25 или укажите её: файл `horizon-mods/.horizon-mod-data/kiln/java_home` с путём к Java
  (папка, где лежит `bin/java`), или переменная окружения `KILN_JAVA_HOME`.
- Мод не запустился — причина в чате и в `…/kiln/java.log`.
- Kiln в Options > Mods можно выключить: он перестанет работать сразу, из сборки уйдёт при следующей.
- Сборка падает на другой версии Horizon: добавьте к команде `--version nightly-2026-10-29-5e391eb` (если эта
  сборка ещё установлена в лаунчере) — Kiln 0.1.0 проверен на ней.

## Только таймер в обычном Minecraft

`kiln-timer-0.1.0-alpha.jar` — обычный Fabric-мод: Minecraft 26.3, Fabric Loader 0.20.1+, Fabric API.
Положите его в `mods/` вашего инстанса Fabric.

---

## English

Alpha: tested on macOS (Apple Silicon) with Horizon Nightly 2026-10-29 · 5e391eb; not tried on Windows or Linux.

| | |
|---|---|
| `kiln-native/` | Kiln, the native mod (an unpacked `.hnmod`) the launcher builds into the game |
| `kiln-0.1.0.hnmod` | the same, as one file |
| `kiln/` | for the game folder: `kiln-runtime.jar` (Kiln's Java side) and `mods/kiln-timer-0.1.0-alpha.jar` |
| `kiln-timer-0.1.0-alpha.jar` (separate) | the same timer for Minecraft 26.3 with Fabric |

You need the Horizon launcher with 26.3 prepared, and Java 25 (Kiln finds it: `JAVA_HOME`, the Minecraft
launcher's Java, installed JDKs).

1. **Read Kiln's code** (`kiln-native/mixins/kiln/`): native mods run unsandboxed, with your rights.
2. **Build Kiln into the game:**
   ```sh
   /Applications/Horizon.app/Contents/MacOS/horizon-launcher native-build ~/Downloads/horizon-kiln-0.1.0-alpha/kiln-native --trust-native-code
   ```
   The first build downloads the launcher's Rust and takes about ten minutes. The game shows up as the
   **Native mods** instance, with its own worlds and options.
3. **Play Native mods** and enter a world: Kiln names its folder in chat. Quit.
4. **Copy** the archive's `kiln/` contents (`kiln-runtime.jar`, `mods/`) into that `…/kiln` folder.
5. **Play:** in a new world the timer starts by itself. Keys: **J** start / split, **K** reset, **U** hide.
   Settings: `…/kiln/config/kiln_timer/settings.properties`. Single player only; not a speedrun.com timer.
