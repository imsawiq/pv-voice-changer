# NeoForge 26.1 Port Scaffold

This folder is intentionally not part of `build-all.bat` yet.

Porting checklist:

- Replace `neoforge_version=TODO` in `gradle.properties`.
- Add the NeoForge-compatible Plasmo Voice dependency once the matching artifact is known.
- Move Fabric client initialization from `ClientModInitializer` to a NeoForge `@Mod` entrypoint.
- Replace Fabric lifecycle/tick events with NeoForge events.
- Replace `FabricLoader` version lookups with NeoForge loader metadata.
- Re-enable and verify mixins against the NeoForge Plasmo Voice classes.
