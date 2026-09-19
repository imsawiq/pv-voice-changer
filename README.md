[![Github](https://cdn.modrinth.com/data/cached_images/53263a70bb7a7689208e40d465e4377be013ccca.png)](https://github.com/imsawiq)
[![Modrinth](https://cdn.modrinth.com/data/cached_images/9ed85626563f727c2ed87c83264a98ed48481756.png)](https://modrinth.com/mod/plasmo-voice-voice-changer)
[![Discord](https://cdn.modrinth.com/data/cached_images/be7e1b5fe7e280c38c5b29dd81c453ddfdad25c2.png)](https://discord.com/invite/nPbxmeTnBQ)
[![Modrinth](https://cdn.modrinth.com/data/cached_images/30e3cf2e35755bad5f6e96d37de5f6aa3e6f0094.png)](https://modrinth.com/mod/plasmo-voice)
[![Modrinth](https://cdn.modrinth.com/data/cached_images/671c63d16316921195f7fc1cdb4ddaa510584a9a.png)](https://modrinth.com/mod/plasmo-voice-voice-changer)
# EN

Fabric and NeoForge mod for **Plasmo Voice** that adds a full voice changer with built-in presets, custom tuning, and saved profiles. The voice is changed on your own client; install it on the server as well to set a policy for everyone and to hand out your own voices.


### Repository layout

- `versions/fabric-26.1` - Fabric build for Minecraft 26.1
- `versions/fabric-26.2` - Fabric build for Minecraft 26.2
- `versions/fabric-26.3` - Fabric build for Minecraft 26.3
- `versions/neoforge-1.21` - NeoForge build for Minecraft 1.21.8
- `versions/neoforge-26.1` - NeoForge build for Minecraft 26.1
- `versions/neoforge-26.2` - NeoForge build for Minecraft 26.2
- `versions/neoforge-26.3` - NeoForge build for Minecraft 26.3
- `build-version.bat fabric-26.3` - build one version (also accepts `fabric-26.1`, `fabric-26.2`, `neoforge-1.21`, `neoforge-26.1`, `neoforge-26.2`, `neoforge-26.3`)
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


### 📦 Dependencies

- **Fabric API** - Required
- **Plasmo Voice** - Required


### 🔗 Links

- **GitHub:** [imsawiq/pv-voice-changer](https://github.com/imsawiq/pv-voice-changer)
- **Discord:** [Preset sharing server](https://discord.gg/UTxEy4PtSU)
- **Issues:** [Report a bug or suggestion](https://github.com/imsawiq/pv-voice-changer/issues)


# RU

Мод для **Plasmo Voice** под Fabric и NeoForge, который добавляет полноценный войсченджер с готовыми пресетами, кастомной настройкой и сохранением профилей. Голос меняется на твоём клиенте; поставь мод ещё и на сервер, чтобы задавать правила для всех и раздавать свои голоса


### Структура репозитория

- `versions/fabric-26.1` - Fabric-сборка для Minecraft 26.1
- `versions/fabric-26.2` - Fabric-сборка для Minecraft 26.2
- `versions/fabric-26.3` - Fabric-сборка для Minecraft 26.3
- `versions/neoforge-1.21` - NeoForge-сборка для Minecraft 1.21.8
- `versions/neoforge-26.1` - NeoForge-сборка для Minecraft 26.1
- `versions/neoforge-26.2` - NeoForge-сборка для Minecraft 26.2
- `versions/neoforge-26.3` - NeoForge-сборка для Minecraft 26.3
- `versions/neoforge-26.3` - NeoForge-сборка для Minecraft 26.3
- `build-version.bat fabric-26.3` - собрать одну версию (также принимает `fabric-26.1`, `fabric-26.2`, `neoforge-1.21`, `neoforge-26.1`, `neoforge-26.2`, `neoforge-26.3`)
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


### 📦 Зависимости

- **Fabric API** - Обязательно
- **Plasmo Voice** - Обязательно


### 🔗 Ссылки

- **GitHub:** [imsawiq/pv-voice-changer](https://github.com/imsawiq/pv-voice-changer)
- **Discord:** [Сервер для обмена пресетами](https://discord.gg/UTxEy4PtSU)
- **Issues:** [Сообщить о баге или предложении](https://github.com/imsawiq/pv-voice-changer/issues)
