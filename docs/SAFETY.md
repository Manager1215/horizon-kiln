# What Kiln runs, with what rights

Kiln is a native Horizon mod. Horizon's launcher builds native mods into a game of their own, only after you read
the warning and agree; they are not sandboxed and run with your rights. Kiln then starts Java inside the game's
process (or, where it can't, in a process of its own beside the game) and runs the mods you put in `kiln/mods/`
there, with the same rights. That is what a Fabric mod has in Minecraft itself.

What that means:

- A Java mod can do what you can on your computer: read and write files, open network connections, start programs.
  Install only mods whose authors you trust, as you would in Minecraft. Modrinth's checksums are checked by the
  launcher when it carries a mod's data; Kiln checks nothing about a jar's intent.
- Kiln's own code is open (GPL-3.0) and small enough to read: the Rust side under `kiln/src/`, the Java side under
  `kiln/java/`. Read it before you build it.
- Nothing in a mod is decompiled, translated or patched. Kiln's stand-ins for Minecraft's classes and the Fabric API
  share only their names and signatures; the repository holds no Minecraft code, assets or files.
- Java runs inside the game's process: a mod could read the game's memory, as a Fabric mod can Minecraft's, and a
  mod that hangs or crashes Java can stop the game. To keep Java apart from the game as before (a stuck mod stops
  only Java), start the game with `KILN_JVM=process`.

## What Kiln asks online

Once a day at most, when the game starts, Kiln fetches the mod catalogue's copy that its overlay reads (which mods
were tried and work, and what a switch can turn off now), on a thread of its own: the game never waits for it.

- **Where:** `https://horizon-kiln.cc/catalogue.json`, which the catalogue rewrites each night; when the site doesn't
  answer with it, `https://raw.githubusercontent.com/Manager1215/horizon-kiln/catalogue/catalogue.json` (GitHub).
- **What the site sees:** your address, once a day, and a request with nothing in it but Kiln's version (`User-Agent:
  Kiln/<version>`) and, when the file is unchanged since yesterday, its tag (`If-None-Match`), so the answer is
  "unchanged" and no file comes. No query, no cookies, no account, **nothing about your mods**: the list in
  `kiln/mods/` never leaves your computer; Kiln matches it against the copy on your side.
- **What it keeps:** the copy in `kiln/cache/catalogue.json`, beside the time of the last request
  (`catalogue.checked`) and the file's tag (`catalogue.etag`). A copy that isn't the catalogue's, or isn't newer than
  the one in use, is left out.
- **What it does with it:** the overlay shows each mod's status, and Kiln's version with a line when a newer release
  is out. Kiln downloads nothing else: a new release is yours to fetch from the site.
- **Off:** a file `catalogue_refresh` in Kiln's folder that says `off`, or `KILN_CATALOGUE_REFRESH=off`; then no
  request goes out and the overlay uses the copy Kiln came with.

## When a mod breaks the game

Kiln catches what Java lets it catch, and the game goes on:

- A mod's exception never leaves Kiln's call: it is written to `kiln/java.log`, and the other mods go on.
- A mod that runs Java out of memory or overflows its stack is turned off at once. The chat names the mod and the
  call, and its switch in Options > Mods stays off, from the next start too, until you turn it back on there (or now,
  with `/kiln on <mod id>`). The next start's title screen asks "Start the game without <mod>?" (start without it,
  or turn it back on). The memory it already holds stays taken until the game restarts.
- A mod that calls `System.exit` or `Runtime.halt` gets an error instead; the game doesn't close.
- A mod stuck in one call for 10 seconds is named in the chat. Kiln can't stop a running Java call, so the game may
  not respond until the call returns.

What Kiln can't catch is a crash of the game's process itself: a native crash (Java's own included, since it runs in
the game's process, or a mod's native library), or the game being killed while a mod hung. The process is gone, and
everything in it, Kiln too. For that, Kiln starts a small Java process of its own beside the game. When the game
stops without closing, it shows a window naming the mod and the call that were running (Kiln notes them before
each call into a mod, in `kiln/last-call`) and what Java's crash report says, and asks: **Start without <mod>**
(its switch in Options > Mods goes off), **Start with all mods**, or **Open the log**. Closed without an answer, or
on a system with no window to show, the question waits for the next start: the title screen asks it. A Rust
panic in the game is Horizon's to report (its own crash report), not Kiln's.

On the roadmap: running the Java process inside an operating-system sandbox where the system offers one (file and
network limits on macOS and Linux), opt-in at first. Until then, the rule above stands: trust the author, read the
code.
