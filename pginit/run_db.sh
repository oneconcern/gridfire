#! /bin/zsh

DB_DATA_DIR=/tmp/gridfire_postgres

if [[ -d $DB_DATA_DIR ]]; then
    rm -rf $DB_DATA_DIR
fi

mkdir $DB_DATA_DIR

initdb -D $DB_DATA_DIR

>/tmp/gridfire_pg.log 2>/tmp/gridfire_pg_err.log \
 postgres -D $DB_DATA_DIR
