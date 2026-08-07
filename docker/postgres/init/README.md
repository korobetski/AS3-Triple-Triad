# Schema bootstrap for the development Postgres.

Any `.sql` or `.sh` file in this directory is executed by the Postgres image **once**, when the
data volume is created empty. It is not a migration system: an existing volume ignores this
directory entirely, so a change here only takes effect after `docker compose down -v`.

Deliberately empty for now. The Phase 5 design settles that profiles are server-held
(decision 2), but not yet what a profile or a transcript looks like on the wire — writing a
schema before those are defined would be guessing.

See [09-PHASE-5-NETWORK.md](../../../docs/migration/09-PHASE-5-NETWORK.md).
