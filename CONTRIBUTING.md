# Contributing

Thanks for wanting to help.

## Where things happen

- **This repository** (Manager1215/horizon-kiln) gets each stable release, as one commit with its tag. Pull requests
  aren't merged here; a change that gets in reaches here with the next release.
- **Development** happens in a private repository. Testers and contributors are invited to it.
- **Bugs and ideas:** open an issue here. Say what you did, what you expected, what happened, your OS, your Horizon
  version, and attach `<game folder>/kiln/java.log` if Kiln was involved.
- **Want to join?** Open an issue titled "Joining" and say what you'd like to work on.

## How a change gets in (development repository)

The team (the maintainer and the core developers) pushes straight to `main` in the development repository. Everyone
else, and anything big or risky, goes through a pull request.

Pushing to `main`:
1. Start from a fresh `main`: `git switch main && git pull`.
2. Make one change at a time. Keep commits small, with a short message in English saying what changed and why.
3. Run the tests (README, "Building and trying it"): `cargo test`, and `./gradlew test` in `mods/kiln-timer` or
   `mods/kiln-testbench` if you touched them. Run `kiln/proof.py` too when you changed what Kiln does in the game,
   and `python3 -I tools/check-mods.py` when you changed Kiln: every mod it says works must still pass
   (docs/MOD-CHECKS.md). For new Rust code, `sh tools/mutants.sh` breaks it a little at a time and lists the breaks
   no test notices.
4. `git pull --rebase`, so your commits go on top of what others pushed meanwhile; if something came in, run the
   tests again.
5. `git push`. Never `git push --force` to `main`, and never rewrite commits that are already pushed.

Through a pull request (new contributors; big, risky or unsure changes):
1. `git switch main && git pull`, then `git switch -c <you>/<what>` (e.g. `egor/keypad-keys`).
2. Commit and test as above, push the branch, open a pull request into `main`. Describe what changed, how you tested
   it, and what you didn't test.
3. The maintainer reviews and merges.

## Rules

- **English** everywhere: code, comments, docs, commits, issues.
- **No Minecraft or Horizon files** in the repository: no code, assets, textures, sounds, decompiled classes or
  screenshots with game textures. Stand-ins repeat only the names and signatures of the classes mods call.
- **Don't change Horizon.** Kiln works only through Horizon's official native-mod mechanisms.
- **Your git identity** before your first commit: `git config user.name "<your name or nickname>"` and
  `git config user.email "<id>+<login>@users.noreply.github.com"` (GitHub, Settings > Emails), so commits are
  yours and your address stays private.
- **Clone outside synced folders**: not in an iCloud-synced Desktop or Documents, OneDrive or Dropbox. They copy
  files while builds rewrite them (`Foo 2.java`) and break the build.
- **Tools are yours to choose**, AI included. You are the author of what you submit: read it, understand it, test it.
  Files carry no tool's name or branding.

## The CLA

Kiln is GPL-3.0-only. Before your first pull request is merged, you agree to the [Contributor License
Agreement](CLA.md) by writing in the pull request:

> I have read the CLA in CLA.md and I agree to it.

You keep the copyright to your work; the CLA lets the maintainer license the project under other terms too.
