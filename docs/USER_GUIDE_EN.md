# Callout — User Guide

Mod version: 1.1.2 · Minecraft 26.3 · Fabric · client-only

[Русская версия](USER_GUIDE_RU.md)

## Contents

1. [What the mod does](#what-the-mod-does)
2. [Installation](#installation)
3. [Getting started](#getting-started)
4. [Triggers](#triggers)
5. [Ping history](#ping-history)
6. [Settings](#settings)
7. [Files](#files)
8. [FAQ](#faq)
9. [Building from source](#building-from-source)

## What the mod does

Callout watches chat and plays a sound when a message contains your name or a word you chose. Every such message (a "ping") goes into a history together with a few messages before and after it, so when you come back from AFK you see who called you and why.

- Your name and any words, as plain text or regular expressions.
- Its own sound, volume and pitch for every trigger.
- A history window with search, context, a per-server filter and sorting.
- Clicking a name in the history opens chat with a ready private message command.

The mod runs only on your side: it sends nothing to the server and the server cannot notice it.

The long chat history and chat restore after rejoining used to be part of Callout; since 1.1.1 they live in [QualityEssentials](https://github.com/ServChan/QualityEssentials). The old `config/callout_sessions/` folder can be deleted.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) `0.19.3` or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3 into the `mods` folder.
3. Put `callout-1.1.2.jar` into `mods` (see [Building](#building-from-source)).
4. Recommended: [Mod Menu](https://modrinth.com/mod/modmenu); the settings open only through it.

Java 25 is required.

## Getting started

1. Join a server. Your name is already watched: the main trigger is empty by default, and an empty word means "my name".
2. Ask someone to type your name in chat; a sound plays.
3. Press `'` (apostrophe) to open the ping history.
4. To add your own words (your clan name, "admin", "help"), open the settings through Mod Menu.

Your own messages do not count by default: if you type your own name, there is no sound, and the mod tells you where to turn that on.

## Triggers

### Main trigger

Your name. If the Word field is empty, the mod uses the name of the current account. You can type another name or pattern.

### Additional triggers

The Add button creates a new trigger. Each has:

| Field | Meaning |
|---|---|
| Word | text or a regular expression |
| Mode | Text matches as is; Regex is a Java regular expression |
| Sound id | a Minecraft sound such as `minecraft:block.note_block.pling` or `minecraft:entity.experience_orb.pickup` |
| Volume | 0 to 4 |
| Pitch | 0.5 to 2 |

Remove deletes a trigger. Only a few triggers fit on screen; the rest go on further pages.

Regular expression examples:

- `(?U)\badmin\w*` matches "admin", "admins", "Administrator";
- `(?U)\b(help|sos)\b` matches either word;
- `^\[Clan\]` matches messages starting with `[Clan]`.

Add the `(?U)` flag when an expression uses `\b` or `\w` with non-Latin letters: without it Java does not treat Cyrillic as letters. Case is ignored even without a flag while Case sensitive is off.

An invalid expression shows an error, and the settings cannot be saved until it is fixed.

### What is checked

- Message text is cleaned of service marks added by other mods (ChatHeads, for example).
- In singleplayer and on LAN the technical sender prefix is dropped so your own messages do not count as pings.
- Case is ignored by default, for both text and regular expressions.

## Ping history

Opens with `'` (apostrophe; change it in **Options → Controls → Callout**).

- Each ping shows its number, time, sender and trigger, with the messages before and after it below.
- **Search** by text at the top of the window.
- **World filter**: all, or one particular server or world, handy for going through the last session's pings.
- **Sorting**: newest or oldest first.
- **Click a name** to open chat with `/msg <name> ` (the template can be changed in the settings file).
- **Click a line** to copy its text to the clipboard.
- Previous, Next and the mouse wheel turn pages.
- Clear deletes the history after a second confirmation ("Confirm?").

## Settings

Open **Mod Menu → Callout → settings icon**. Save applies, Cancel closes without saving.

| Setting | Default | What it does |
|---|---|---|
| Enabled | on | the main switch |
| Case sensitive | off | tell "Name" from "name" |
| Own messages | off | trigger on your own messages too |
| Main trigger | your name, sound `block.note_block.pling`, volume 1, pitch 1 | see above |
| Additional triggers | none | your own words and expressions |
| Max pings | 100 | how many pings to keep, 1 to 1000 |
| Before / After | 5 / 5 | how many context messages to keep around a ping, 0 to 20 |
| Persist history | on | keep the history between game launches |
| Clear on world switch | off | wipe the history when you move to another server or world |

Only in `config/callout.json`:

| Key | Default | What it does |
|---|---|---|
| `whisperCommand` | `/msg %s ` | the command opened when you click a name; `%s` becomes the name |
| `separateHistoryByWorld` | `false` | with `true`, worlds behind one server address get separate histories (by spawn point) |

## Files

- `config/callout.json` holds the settings.
- `config/callout_history.json` holds the ping history.

Both are written atomically with `.bak` backups, which the mod restores from if the main file is damaged.

## FAQ

**No sound although someone wrote my name.** Check that the mod is on and that you are not testing with your own message (your own messages do not count by default). Check the game's master volume and the trigger's own volume.

**It triggers on other names that contain mine.** Use Regex mode with word boundaries: `(?U)\bMyName\b`.

**I want a different sound.** Enter the id of any Minecraft sound, such as `minecraft:entity.player.levelup`. The `/playsound` command suggests the ids in game.

**The history is empty after a restart.** Check that Persist history is on.

**Where is the long chat history?** It moved to [QualityEssentials](https://github.com/ServChan/QualityEssentials), in its Chat module.

## Building from source

JDK 25 is required.

```powershell
git clone https://github.com/ServChan/Callout.git
cd Callout
.\gradlew.bat build
```

On Linux and macOS use `./gradlew`. The jar is `build/libs/callout-1.1.2.jar`.
