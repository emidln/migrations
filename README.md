# migrations

A Clojure migrations library driven by hate and NIH. If you want transactional DDL, BEGIN; ... COMMIT; yourself in your .sql file. Not particularly opinionated about which database you use, but we only implement multimethods for postgresql using `psql(1)`, so YMMV. 

## Configuration

Add these `:dependencies` in your `project.clj`:

```clj
[com.emidln/migrations "0.1.0-SNAPSHOT"]
```

Add this to your `:plugins` in your `project.clj`:

```clj

[com.emidln/lein-migrations "0.1.0-SNAPSHOT"]

```

Add this to your `project.clj`:

```clj

:migrations {
    ;; where do we store migrations?
        :migrations-dir "resources/migrations/"
        ;; what do we call the table to handle the migrations?
        :migrations-table "schema_migrations"
        ;; create database if doesn't exist?
        :migrations-create-database? true
        ;; where can I find my database?
        :db ~(get (System/getenv) "DB_URI")
}

```


## Migrations

Migrations are .sql files with the following naming convention:

`integer-name_of_migration.direction.sql`

This looks like the following in practice:

`01-initial.up.sql`
`01-initial.down.sql`

The integer need not be sequential (this is useful if you happen to have topic-based branches in a DVCS), but migrations are applied in ascending order. You can create new migrations via the `create` command, which defaults to unix timestamp as the integer.


## Usage

Apply all pending migrations:

```bash

lein migrate up

```

Remove the most recent applied migration:

```bash

lein migrate down

```

List all migrations and their status:

```bash

lein migrate status

```

Create a new migration:

```bash

lein migrate create [name]

```

All commands are also available in the com.emidln.migrations namespace via `run`. See docstrings for more info.

## Implementation Notes

migrations currently uses psql(1). It probably doesn't work too well with other databases. PRs welcome, just implement the multimethods on your scheme. 

## License

Copyright © 2016 Brandon Adams

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
