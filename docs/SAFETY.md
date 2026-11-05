# What Kiln runs, with what rights

Kiln is a native Horizon mod. Horizon's launcher builds native mods into a game of their own, only after you read
the warning and agree; they are not sandboxed and run with your rights. Kiln then starts Java beside the game and
runs the mods you put in `kiln/mods/` in that Java process, with the same rights. That is what a Fabric mod has in
Minecraft itself.

What that means:

- A Java mod can do what you can on your computer: read and write files, open network connections, start programs.
  Install only mods whose authors you trust, as you would in Minecraft. Modrinth's checksums are checked by the
  launcher when it carries a mod's data; Kiln checks nothing about a jar's intent.
- Kiln's own code is open (GPL-3.0) and small enough to read: the Rust side under `kiln/src/`, the Java side under
  `kiln/java/`. Read it before you build it.
- Nothing in a mod is decompiled, translated or patched. Kiln's stand-ins for Minecraft's classes and the Fabric API
  share only their names and signatures; the repository holds no Minecraft code, assets or files.
- Kiln's Java process can see the game's state Kiln sends it and draw on the HUD; it cannot read Horizon's memory.
  A mod that hangs or crashes stops its own process, not the game (since 0.1.1).

On the roadmap: running the Java process inside an operating-system sandbox where the system offers one (file and
network limits on macOS and Linux), opt-in at first. Until then, the rule above stands: trust the author, read the
code.
