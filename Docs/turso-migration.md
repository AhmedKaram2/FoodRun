# Move FoodRun storage to Turso

The Turso account dashboard is not a database connection URL. Create a database on the Free plan and keep overages disabled. Obtain its database URL (`libsql://…turso.io` or `turso://…turso.io`) and a database read/write token. Store the token in Render's secret environment settings; do not put it in Git, frontend variables, chat, or command arguments.

The server supports `FOODRUN_STORAGE=turso` with these server-only settings:

- `FOODRUN_TURSO_URL`: the database connection URL.
- `FOODRUN_TURSO_AUTH_TOKEN`: a database read/write token.
- `FOODRUN_TURSO_NAMESPACE`: a stable namespace such as `foodrun-production`.
- `FOODRUN_TURSO_BOOTSTRAP`: leave unset or `false` for an existing production app.
- `FOODRUN_TURSO_DAILY_WRITES`: optional guard, default and maximum `12000`, reset at midnight Pacific. Migration and writer claims count too.

Firebase Auth, existing Firebase identities and Intrvioo integration remain configured. Rooms, wallet entries, payment idempotency records, sessions, profiles and account records persist in Turso. Legacy Firestore profile synchronization is optional background work; its quota does not block Turso sign-in or registration. Render stays on its Free instance; its idle cold start still applies.

## Migrate the existing data before switching

Stop the old FoodRun API before migration, and retain the Firestore source. Never initialize an empty production Turso namespace as a replacement for existing wallet and room data. A new account or database does not copy the source automatically.

Build the server distribution and run the migration entry point with the existing Firestore credentials and the new Turso settings supplied securely as environment variables:

```sh
./gradlew --settings-file settings-server.gradle.kts :room-server:installDist
java -Xmx192m -XX:+UseSerialGC -cp 'room-server/build/install/room-server/lib/*' \
  com.karim.foodrun.server.MigrateFirestoreToTurso --old-server-stopped
```

The migration claims the old writer, streams existing rows into a new Turso namespace, reads back every row, and compares the complete set of IDs and content hashes before publishing the initialized target. Source records are preserved. An interrupted import remains unavailable and must be inspected; a new empty migration namespace can be used for a retry. An existing target namespace is never overwritten. If Firestore's read quota is exhausted, migration still requires its next quota reset or a separately verified existing backup; creating an empty database cannot recover the missing data.

After a verified migration, set Render's `FOODRUN_STORAGE=turso`, keep `FOODRUN_TURSO_BOOTSTRAP=false`, run `Scripts/verify-render-storage.py` with the service ID, and deploy the tested commit. Verify the deployed commit, HTTP 200 from `/health`, and `X-FoodRun-Storage: turso`. Do not remove the source until a real account's rooms, wallet, history and sign-in are verified.

## Persistence and failure behavior

Remote rows are authoritative. Startup restores bounded compressed pages into one encrypted local SQLite transaction. Every change commits its metadata revision and all affected rows in one conditional SQL transaction. A failed statement skips COMMIT and rolls back. A lost response is checked against a unique commit marker rather than replaying the mutation. A new server atomically claims writer ownership; commits from the old server fail, and cached reads stop on its next ownership check (at most one second between checks). Connection or ownership failures make the cache unavailable until a fresh restore.

Rows are compressed independently and indexed by namespace and row ID. Startup first reads bounded payload sizes, then fetches only a bounded compressed page. Writes update only changed rows; idle room ticks do not rewrite the store. A persisted guard caps counted write operations at 12,000 per Pacific day, compressed rows plus estimated per-row overhead at 512 MiB, and total rows at 15,000. The database also has a 600 MiB provider size limit to leave space for SQLite indexes and metadata. A refused write never replaces a previously confirmed record. These are application guards; Turso's account quotas are shared with any other databases, and paid overages must remain disabled.

The source defaults in `render.yaml` stay on Firestore until the actual migration has been verified and the live configuration is switched. No billing plan is upgraded by this change.

## A temporary Turso cutover

An empty Turso database cannot serve existing accounts safely while their only complete copy is blocked by Firestore's quota. First obtain a complete current source snapshot, migrate it, verify every row, and stop the previous writer before switching. Preserve the original Firestore namespace throughout.

While Turso is active, new rooms, payments and account changes belong to Turso. After Firestore's quota resets, changing the environment back to the old Firestore namespace would discard those newer changes. Copy and verify the complete latest Turso state into a fresh Firestore namespace before switching back, retain both previous copies, and verify account balances and command retry records. Automatic failover, continuous bidirectional synchronization and the reverse migration tool are separate work; having two adapters does not enable those behaviors.
