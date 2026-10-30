# horizon-kiln

[English](README.md)

**Kiln запускает Java-моды в [Horizon](https://github.com/horizon-rust/horizon)** — Minecraft: Java
Edition 26.3, переписанный на Rust. Так же, как Proton запускает Windows-игры на Linux: код мода выполняется
как есть, а его обращения к классам Майнкрафта и Fabric API превращаются в вызовы Horizon.

> **Состояние: альфа 0.1.0** ([установка](INSTALL.md)). Kiln запускает клиентские Fabric-моды, которые пользуются немногими классами, уже
> известными ему; первый такой мод — наш таймер для спидранов. Проект не связан с Horizon, Mojang и Microsoft;
> таймер не одобрен speedrun.com.

## Идея

1. Один раз ставите **Kiln**. Как и любой нативный мод Horizon, лаунчер один раз встраивает его в игру
   после предупреждения (лаунчер говорит о 20–30 минутах; у альфы на Mac с Apple Silicon первая сборка заняла
   около 10).
2. Кладёте `.jar`-моды в `<папка игры>/kiln/mods/`.
3. Играете.

Kiln запускает Java рядом с игрой (внутри нельзя: Horizon запрещает небезопасный код), выполняет точку входа
каждого мода из его `fabric.mod.json` и дальше 20 раз в секунду отдаёт ему тик клиента с состоянием игры,
нажатыми клавишами и полученными достижениями, а то, что мод рисует, выводит на HUD. План, риски и всё, что мы
узнали про Horizon и Minecraft 26.3, — в [PROTON.md](PROTON.md); все решения и их причины — в
[DECISIONS.md](DECISIONS.md).

## Kiln Speedrun Timer

Fabric-мод для Minecraft 26.3 (`mods/kiln-timer`); тот же jar работает в Horizon через Kiln. Проверено и там, и
там: `./gradlew runClientGameTest` проходит ран в Minecraft с Fabric, `kiln/proof.py` — в Horizon.

- **Any%** с автосплитами: Nether, Bastion, Fortress, Blind, Stronghold, End, Finish. Срабатывают по
  достижениям и смене измерения, каждый один раз, в любом порядке.
- **RTA** и **IGT**: IGT — реальное время без меню паузы и экранов загрузки, как в SpeedRunIGT.
- **Старт:** в новом мире сам, в старом — клавишей сплита. Выход из мира сбрасывает таймер: незаконченный ран
  пропадает (его сплиты всё равно идут в лучшие сплиты), законченный уже сохранён.
- **Финиш:** дракон («Free the End», по умолчанию), возвращение из Энда в обычный мир или вручную
  (`manual`: клавиша сплита, когда остальные сплиты пройдены; каждое нажатие ставит следующий по порядку).
- **Личный рекорд** и лучшие сплиты, дельты к PB (зелёный — быстрее, красный — медленнее), «PB!», когда побит.
- **Клавиши:** J — старт / сплит, K — сброс, U — скрыть панель.
- **Настройки:** `<config>/kiln_timer/settings.properties`: `corner` (`top_left`, `top_right`, `bottom_left`,
  `bottom_right`), `finish` (`dragon`, `leave_end`, `manual`), `shown`, `chat_splits`. Рекорды — `records.txt`
  рядом. Под Kiln папка config — `<папка игры>/kiln/config`.
- **Ограничения:** одиночная игра (достижения берутся у сервера одиночной игры); панель обновляется 20 раз в
  секунду; чат-команды пока нет; под Kiln клавиши не переназначаются (Kiln слушает клавиши по умолчанию); не
  одобрен speedrun.com.

## Устройство

```
kiln/                      нативный мод Horizon
├─ mixins/kiln/            бандл, который лаунчер встраивает в игру
├─ java/                   Java-часть Kiln (kiln-runtime.jar) и заглушки классов Minecraft и Fabric
├─ src/, tests/            cargo test (tests/ — с настоящей Java)
├─ proof.py                Kiln и таймер в запущенной игре через horizon-ctl
└─ compatibility.json      коммит Horizon и версия hook points, на которых он собирается
mods/kiln-timer/           таймер для спидранов: Fabric-мод для Minecraft 26.3 (Gradle)
spike/                     эксперименты: JVM внутри программы на Rust, Fabric-мод спайка
tools/build-java.sh        собирает target/kiln/: kiln-runtime.jar и Java-моды
```

## Сборка и проверка

Нужны Rust, Git, Python 3.11+, rsync, JDK 25 и клон
[horizon-mods](https://github.com/horizon-rust/horizon-mods) ради его `native/tools/native.py`:

```sh
cargo test                                            # юнит-тесты, без игры
sh tools/build-java.sh                                # target/kiln/: Java-часть Kiln и моды
cargo test -p horizon-kiln-mod -- --ignored           # Kiln с настоящей Java, без игры
git clone https://github.com/horizon-rust/horizon-mods .deps/horizon-mods
ln -s ../../../kiln .deps/horizon-mods/native/kiln
cd .deps/horizon-mods
python3 native/tools/native.py check kiln             # наложить на исходники Horizon и проверить cargo check
python3 native/tools/native.py proof --resources <подготовленные файлы 26.3> kiln   # таймер в игре
```

Чтобы поиграть — [INSTALL.md](INSTALL.md); `sh tools/package.sh` собирает файлы релиза в `dist/`.

## Лицензия

MIT. В репозитории нет кода, ассетов и файлов Майнкрафта: у заглушек Kiln с ними общие только имена классов и
методов, которые вызывают моды.
