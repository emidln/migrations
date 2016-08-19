# migrations

A Clojure migrations library driven by hate and NIH. If you want transactional DDL, BEGIN; ... COMMIT; yourself in your .sql file. Not particularly opinionated about which database you use, but we only implement multimethods for postgresql using `psql(1)`, so YMMV. 

## Configuration

### In a clojure project
Add these `:dependencies` in your `project.clj`:

[![Clojars Project](https://img.shields.io/clojars/v/com.emidln/migrations.svg)](https://clojars.org/com.emidln/migrations)

### Outside of a clojure project (needs java and lein)

```bash

make && sudo make install

```

### Environment Variables

#### MIGRATIONS_DB_URI

This is the database URI used to store the migrations. When creating this, the postgres driver will
first attempt to connect to the `postgres` database first in order to create this database.  This has
implications w.r.t to database permissioning.

#### MIGRATIONS_DIR

This is the directory your migrations live in. It will be created if it doesn't exist.

#### MIGRATIONS_TABLE

This is the table that we track migrations in. You probably don't have to change this, but you can.

## Migrations

Migrations are .sql files with the following naming convention:

`integer-name_of_migration.direction.sql`

This looks like the following in practice:

`01-initial.up.sql`
`01-initial.down.sql`

The integer need not be sequential (this is useful if you happen to have topic-based branches in a DVCS), but migrations are applied in ascending order. You can create new migrations via the `create` command, which defaults to unix timestamp as the integer.


## Usage

Create the database:

```bash

migrations create-db

```

Apply all pending migrations:

```bash

migrations up

```

Remove the most recent applied migration:

```bash

migrations down

```

List all migrations and their status:

```bash

migrations status

```

Create a new migration:

```bash

migrations create [name]

```

Backfill on-disk migrations to the database (just insert the records, don't run the DDL):

```bash

migrations backfill

```


All commands are also available in the com.emidln.migrations namespace. See docstrings for more info.

## Implementation Notes

migrations currently uses psql(1). It probably doesn't work too well with other databases. PRs welcome, just implement the multimethods on your scheme. 

## License

Copyright © 2016 Brandon Adams

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
