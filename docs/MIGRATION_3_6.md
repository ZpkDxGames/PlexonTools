# Migrating PlexonTools 3.5 to 3.6

## Before upgrading

1. Stop the Paper server.
2. Back up the complete `plugins/PlexonTools` directory, especially `data.yml` and all five administrator YAML files.
3. Replace the old JAR with `PlexonTools-3.6.0.jar`.
4. Start the server and watch the PlexonTools startup log.

PlexonTools creates `plexontools.db` and migrates schema-v3/v4 `data.yml` automatically. Do not create an empty database manually and do not delete the YAML before the first successful startup.

## Expected first startup

- The legacy YAML is fully validated before new database state is accepted.
- A timestamped `data.yml.pre-sqlite-...bak` copy is created.
- The import, verification, and migration marker are committed together.
- The original `data.yml` remains present but is no longer live state.
- Current configuration references appear under `plugins/PlexonTools/examples` without replacing customized live YAML.

If migration validation fails, fix or restore the reported `data.yml` and start again. PlexonTools intentionally refuses partial imports.

## Existing configuration

Existing YAML files remain compatible and are not overwritten. New settings use safe defaults in memory. Compare the live files with the references under `examples`, then copy only the controls you want.

The main new lore layout is `tool-lore.template`; older `default_lore_format` keys remain supported as fallbacks. The main new persistence section is `storage`. Changing database startup options requires a full restart.

## After upgrading

1. Confirm `plexontools.db` exists and startup reports no integrity error.
2. Verify a sample of active and inactive tool UUIDs, levels, progress, and target counters.
3. Run `/pt backup` and confirm a database copy appears under `plugins/PlexonTools/backups`.
4. Retain the original YAML and pre-SQLite backup until the release is fully validated on your server.

Detailed restore and rollback procedures are in [`PLEXONTOOLS_3_6_0.md`](PLEXONTOOLS_3_6_0.md).
