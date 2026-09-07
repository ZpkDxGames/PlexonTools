# PlexonTools 4.1.0

PlexonTools 4.1.0 is a conservative infrastructure/API release built from the verified production 4.0.0 baseline. It preserves existing progressive tools, gameplay, GUI, PDC identities, SQLite data, natural-block provenance, and configuration behavior while making PlexonTools a first-class optional PlexonCore module.

## Integration

- PlexonCore 1.0.0 / Core API `>=1.0 <2.0`
- Safe standalone mode when Core is absent or incompatible
- Public `PlexonToolsAPI` through Bukkit ServicesManager
- Public `PlexonToolProgressEvent` and `PlexonToolLevelUpEvent` for PlexonQuests 3.1.0
- `/pt diagnostics` for runtime integration and persistence health

## Compatibility

- Paper 26.2
- Java 25
- Existing `plugins/PlexonTools/` data/config directory is retained in place
- PlexonCore is compile-only/provided and is not bundled inside PlexonTools

## Upgrade

Stop the server, back up `PlexonTools-4.0.0.jar` and `plugins/PlexonTools/`, replace only the plugin JAR with `PlexonTools-4.1.0.jar`, then start the server. With Core installed, verify `/plexon modules`, `/plexon diagnostics`, `/quests diagnostics`, and `/pt diagnostics`.
