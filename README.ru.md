# horizon-kiln

[English](README.md)

**Kiln запускает Java-моды в [Horizon](https://github.com/horizon-rust/horizon)** — Minecraft: Java
Edition 26.3, переписанном на Rust. Так же, как Proton запускает Windows-игры на Linux: код мода выполняется
как есть, а его обращения к классам Майнкрафта и Fabric API превращаются в вызовы Horizon.

> **Состояние: до альфы.** Пока ничего не запускается: Kiln — пустой нативный мод, а мы выясняем, как
> выполнять Java-код внутри Horizon. Проект не связан с Horizon, Mojang и Microsoft.

## Идея

1. Один раз ставите **Kiln** из лаунчера Horizon. Как и любой нативный мод Horizon, лаунчер один раз
   встраивает его в игру (20–30 минут, после предупреждения).
2. Кладёте `.jar`-моды в папку Kiln.
3. Играете.

Первый Java-мод, который должен так заработать, — наш таймер для спидранов: Fabric-мод для Minecraft 26.3,
который без изменений работает и в Horizon через Kiln. План, риски и всё, что мы узнали про API модов
Horizon, — в [PROTON.md](PROTON.md); все решения и их причины — в [DECISIONS.md](DECISIONS.md).

## Устройство

```
kiln/                      нативный мод Horizon
├─ mixins/kiln/            бандл, который лаунчер встраивает в игру
├─ java/                   Java-часть Kiln (kiln-runtime.jar): работает отдельным процессом
├─ src/, tests/            cargo test (tests/ — с настоящей Java)
├─ proof.py                проверка Kiln в запущенной игре через horizon-ctl
└─ compatibility.json      коммит Horizon и версия hook points, на которых он собирается
mods/kiln-timer/           таймер для спидранов: Fabric-мод для Minecraft 26.3 (Gradle)
spike/                     эксперименты: JVM внутри программы на Rust, Java-мод спайка
tools/build-java.sh        собирает target/kiln/: kiln-runtime.jar и мод спайка
```

В игре Kiln читает `<папка игры>/kiln/kiln-runtime.jar` и Java-моды из `<папка игры>/kiln/mods/`.

## Сборка

Нужны Rust, Git, Python 3.11+ и rsync, а также клон
[horizon-mods](https://github.com/horizon-rust/horizon-mods) ради его `native/tools/native.py`:

```sh
cargo test                                            # юнит-тесты, без игры
git clone https://github.com/horizon-rust/horizon-mods .deps/horizon-mods
ln -s ../../../kiln .deps/horizon-mods/native/kiln
cd .deps/horizon-mods
python3 native/tools/native.py check kiln             # наложить на исходники Horizon и проверить cargo check
python3 native/tools/native.py build kiln             # собрать игру с Kiln (профиль fast)
```

## Лицензия

MIT. В репозитории нет кода, ассетов и файлов Майнкрафта.
