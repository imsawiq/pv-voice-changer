[![Github](https://cdn.modrinth.com/data/cached_images/53263a70bb7a7689208e40d465e4377be013ccca.png)](https://github.com/imsawiq)
[![Modrinth](https://cdn.modrinth.com/data/cached_images/9ed85626563f727c2ed87c83264a98ed48481756.png)](https://modrinth.com/mod/plasmo-voice-voice-changer)
[![Discord](https://cdn.modrinth.com/data/cached_images/be7e1b5fe7e280c38c5b29dd81c453ddfdad25c2.png)](https://discord.com/invite/nPbxmeTnBQ)
[![Modrinth](https://cdn.modrinth.com/data/cached_images/30e3cf2e35755bad5f6e96d37de5f6aa3e6f0094.png)](https://modrinth.com/mod/plasmo-voice)
[![Modrinth](https://cdn.modrinth.com/data/cached_images/671c63d16316921195f7fc1cdb4ddaa510584a9a.png)](https://modrinth.com/mod/plasmo-voice-voice-changer)
# EN

Fabric and NeoForge mod for **Plasmo Voice** that adds a full voice changer with built-in presets, custom tuning, and saved profiles. The voice is changed on your own client; install it on the server as well to set a policy for everyone and to hand out your own voices.


### Repository layout

- the root project - Fabric build for Minecraft 1.21 - 1.21.11
- `versions/fabric-26.1` - Fabric build for Minecraft 26.1
- `versions/fabric-26.2` - Fabric build for Minecraft 26.2
- `versions/fabric-26.3` - Fabric build for Minecraft 26.3
- `versions/neoforge-1.21` - NeoForge build for Minecraft 1.21 - 1.21.11
- `versions/neoforge-26.1` - NeoForge build for Minecraft 26.1
- `versions/neoforge-26.2` - NeoForge build for Minecraft 26.2
- `versions/neoforge-26.3` - NeoForge build for Minecraft 26.3
- `build-version.bat fabric-26.3` - build one version (also accepts `fabric-1.21`, `fabric-26.1`, `fabric-26.2`, `neoforge-1.21`, `neoforge-26.1`, `neoforge-26.2`, `neoforge-26.3`)
- `build-all.bat` - build all Fabric and NeoForge versions into `dist/`
- `scripts/sync-versions.ps1` - copy the shared sources from the root project into every fork. The root is the source of truth; only `client/compat`, `client/ui/compat` and the NeoForge entry points differ per version


### ✨ Features

- **Built-in voice presets** - `Man`, `Woman`, `Kid`, `Titan`, `Demon`, `Batman`, and `Radio`
- **Real outgoing voice processing** - Changes your microphone before Plasmo Voice encodes and sends it
- **Independent pitch and formant** - Move the note and the apparent size of the speaker separately, so a raised voice sounds like a different person instead of a chipmunk
- **Voice Changer Studio** - Opens in a simple mode with one strength control and a microphone meter; an advanced mode exposes every parameter
- **Clean signal path** - Noise gate, de-esser and output limiter, so room noise, sibilance and crackle stay out of the chat
- **Preset saving** - Save your own custom setups as files and load them later
- **Preset sharing community** - Join the Discord server to share your presets and download presets from other users
- **API for other mods** - a radio, a mask or a machine can apply a voice while it is in use and hand it back; mods can also add their own voices to the studio. See [docs/API.md](docs/API.md)
- **Server side** - install it on the server too, to allow or deny the voice changer, mute individual players by command, and hand out your own voices to everyone connected
- **Works without a server** - everything except the server policy runs on your client alone


### ✅ Supported versions

| Minecraft | Fabric | NeoForge |
|---|:---:|:---:|
| 1.21 - 1.21.11 | yes | yes |
| 26.1 - 26.1.2 | yes | yes |
| 26.2 | yes | yes |
| 26.3 | yes | yes |


### 🎮 Getting started

1. Install **Plasmo Voice** and this mod on your client. On Fabric you also need **Fabric API**.
2. Open Plasmo Voice settings, go to the **Activations** tab, and press **Open Voice Changer Studio**.
3. Pick a voice from the grid and set the strength. **Self Listen** plays your own processed voice back so you can hear what everyone else hears.
4. **J** switches the effect on and off without opening anything. Rebind it in the same tab.

Nothing here needs a server: on a server without the mod, everything except the server policy still works.


### 🛠️ For server operators

Install the mod on the server as well. It writes `config/pv-voice-changer/server.properties` on first start, with every key documented inside the file.

Worth being clear first: the voice is changed on the speaker's own machine before it is sent. Everything below is a policy an unmodified client obeys, not something a server can enforce.

**Commands** - `/voicechanger`, or `/vc` for short. Operators only by default.

| Command | What it does |
|---|---|
| `status` | Current policy, how many players are muted, how many voices are shared |
| `on` / `off` | Allows or denies the voice changer for everyone here |
| `restrict <all\|presets\|server-only>` | How much freedom players have over their voice |
| `mute <player>` / `unmute <player>` | Switches one player's voice changer off, and back on |
| `mutelist` | Who is currently muted |
| `voices` | The voices this server shares |
| `reload` | Re-reads the config, the mute list and the shared voices |

**Restrictions** - `all` lets players tune freely. `presets` allows ready-made voices only: built-in, added by another mod, or shared by this server; no hand tuning and no personal preset files. `server-only` allows nothing but the voices this server shares. A voice another mod is holding, such as a radio, is never affected.

**Permissions** - `pv-voice-changer.use` decides who may change their voice (everyone by default), `pv-voice-changer.command` who may run the command (operators by default). They go through Plasmo Voice's own permission layer, so LuckPerms works on both loaders.

**Sharing your own voices** - save a voice in the studio, press **Open Folder**, copy the file into `config/pv-voice-changer/shared-voices/` on the server, then run `/voicechanger reload`. It appears in every connected player's studio and disappears when they leave. Only numbers are read from those files and every value is clamped to its own range, so this cannot make a client load or run anything.

The full reference, including the API for other mods, is in [docs/API.md](docs/API.md).


### 📦 Dependencies

- **Plasmo Voice** - Required
- **Fabric API** - Required on Fabric builds only


### 🔗 Links

- **GitHub:** [imsawiq/pv-voice-changer](https://github.com/imsawiq/pv-voice-changer)
- **Discord:** [Preset sharing server](https://discord.gg/UTxEy4PtSU)
- **Issues:** [Report a bug or suggestion](https://github.com/imsawiq/pv-voice-changer/issues)


# RU

Мод для **Plasmo Voice** под Fabric и NeoForge, который добавляет полноценный войсченджер с готовыми пресетами, кастомной настройкой и сохранением профилей. Голос меняется на твоём клиенте; поставь мод ещё и на сервер, чтобы задавать правила для всех и раздавать свои голоса


### Структура репозитория

- корневой проект - Fabric-сборка для Minecraft 1.21 - 1.21.11
- `versions/fabric-26.1` - Fabric-сборка для Minecraft 26.1
- `versions/fabric-26.2` - Fabric-сборка для Minecraft 26.2
- `versions/fabric-26.3` - Fabric-сборка для Minecraft 26.3
- `versions/neoforge-1.21` - NeoForge-сборка для Minecraft 1.21 - 1.21.11
- `versions/neoforge-26.1` - NeoForge-сборка для Minecraft 26.1
- `versions/neoforge-26.2` - NeoForge-сборка для Minecraft 26.2
- `versions/neoforge-26.3` - NeoForge-сборка для Minecraft 26.3
- `versions/neoforge-26.3` - NeoForge-сборка для Minecraft 26.3
- `build-version.bat fabric-26.3` - собрать одну версию (также принимает `fabric-1.21`, `fabric-26.1`, `fabric-26.2`, `neoforge-1.21`, `neoforge-26.1`, `neoforge-26.2`, `neoforge-26.3`)
- `build-all.bat` - собрать все Fabric- и NeoForge-версии в `dist/`
- `scripts/sync-versions.ps1` - разнести общие исходники из корневого проекта по всем форкам. Корень — источник правды; по версиям различаются только `client/compat`, `client/ui/compat` и точки входа NeoForge


### ✨ Особенности

- **Готовые голосовые пресеты** - `Man`, `Woman`, `Kid`, `Titan`, `Demon`, `Batman` и `Radio`
- **Реальная обработка исходящего голоса** - Меняет микрофон ещё до кодирования и отправки через Plasmo Voice
- **Раздельные высота и форманта** - Ноту и кажущийся размер говорящего можно двигать отдельно, поэтому поднятый голос звучит как другой человек, а не как бурундук
- **Студия войсченджера** - Открывается в простом режиме с одной ручкой силы и индикатором микрофона; в продвинутом доступны все параметры
- **Чистый тракт** - Шумоподавление, де-эссер и лимитер, чтобы шум комнаты, шипение и треск не уходили в чат
- **Сохранение пресетов** - Можно сохранять свои кастомные настройки в файлы и загружать их
- **Discord-сервер с пресетами** - Можно делиться своими пресетами и скачивать пресеты других пользователей
- **API для других модов** - рация, маска или машина могут подменить голос на время и вернуть обратно; моды могут добавлять свои голоса в студию. См. [docs/API.md](docs/API.md)
- **Серверная часть** - мод ставится и на сервер: разрешить или запретить изменение голоса, замутить игрока командой, раздавать свои голоса всем, кто зашёл
- **Работает и без сервера** - всё, кроме серверной политики, живёт на клиенте


### ✅ Поддерживаемые версии

| Minecraft | Fabric | NeoForge |
|---|:---:|:---:|
| 1.21 - 1.21.11 | да | да |
| 26.1 - 26.1.2 | да | да |
| 26.2 | да | да |
| 26.3 | да | да |


### 🎮 С чего начать

1. Поставь **Plasmo Voice** и этот мод на клиент. На Fabric нужен ещё **Fabric API**.
2. Открой настройки Plasmo Voice, вкладку **Активации**, и нажми **Открыть студию войсченджера**.
3. Выбери голос из сетки и задай силу. **Self Listen** проигрывает тебе твой же обработанный голос — слышно ровно то, что слышат другие.
4. **J** включает и выключает эффект, ничего не открывая. Переназначить можно там же.

Сервер для этого не нужен: на сервере без мода работает всё, кроме серверных правил.


### 🛠️ Для владельцев серверов

Поставь мод и на сервер. При первом запуске он создаст `config/pv-voice-changer/server.properties`, где каждый ключ описан прямо в файле.

Сразу честно: голос меняется на машине говорящего до отправки. Всё ниже — правило, которому следует неизменённый клиент, а не то, что сервер может навязать.

**Команды** — `/voicechanger`, коротко `/vc`. По умолчанию только для операторов.

| Команда | Что делает |
|---|---|
| `status` | Текущие правила, сколько игроков заглушено, сколько голосов раздаётся |
| `on` / `off` | Разрешает или запрещает войсченджер всем здесь |
| `restrict <all\|presets\|server-only>` | Насколько свободно игроки распоряжаются голосом |
| `mute <игрок>` / `unmute <игрок>` | Выключает войсченджер одному игроку и включает обратно |
| `mutelist` | Кто сейчас заглушён |
| `voices` | Голоса, которые раздаёт этот сервер |
| `reload` | Перечитывает конфиг, список мутов и раздаваемые голоса |

**Ограничения** — `all` разрешает настраивать что угодно. `presets` оставляет только готовые голоса: встроенные, добавленные другим модом или раздаваемые сервером; ручной настройки и личных файлов пресетов нет. `server-only` — только голоса этого сервера. Голос, который держит другой мод, например рация, под ограничения не попадает никогда.

**Права** — `pv-voice-changer.use` решает, кому можно менять голос (по умолчанию всем), `pv-voice-changer.command` — кому можно выполнять команду (по умолчанию операторам). Идут через штатный слой прав Plasmo Voice, так что LuckPerms работает на обоих лоадерах.

**Раздача своих голосов** — сохрани голос в студии, нажми **Открыть папку**, скопируй файл в `config/pv-voice-changer/shared-voices/` на сервере и выполни `/voicechanger reload`. Он появится в студии у всех подключённых и пропадёт, когда они выйдут. Из этих файлов читаются только числа, и каждое значение ограничено своим диапазоном, так что заставить клиент что-то загрузить или выполнить через это нельзя.

Полный справочник, включая API для других модов, — в [docs/API.md](docs/API.md).


### 📦 Зависимости

- **Plasmo Voice** - Обязательно
- **Fabric API** - Обязательно только для сборок под Fabric


### 🔗 Ссылки

- **GitHub:** [imsawiq/pv-voice-changer](https://github.com/imsawiq/pv-voice-changer)
- **Discord:** [Сервер для обмена пресетами](https://discord.gg/UTxEy4PtSU)
- **Issues:** [Сообщить о баге или предложении](https://github.com/imsawiq/pv-voice-changer/issues)
