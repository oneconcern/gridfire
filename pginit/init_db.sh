#! /bin/zsh

PG_USER=postgres

createuser -s $PG_USER


print -- 'CREATE ROLE gjohnson WITH LOGIN CREATEDB;
CREATE DATABASE gridfire WITH OWNER gjohnson;
\c gridfire
CREATE EXTENSION postgis;' | \
    psql -U $PG_USER


srid=4326
# raster=dem.tif
raster=test/input_data/tubbs_1507528800_1507530600.tif
table=dem
database=gridfire
raster2pgsql -s $srid $raster $table |psql -U $database

FUEL_TAGS=('dem' 'slp' 'asp' 'fbfm40' 'cc' 'ch' 'cbh' 'cbd')
typeset -i fuel_tag_idx
fuel_tag_idx=0

lcp_file_path=test/input_data/lcp_tubbs_fire.lcp

for fuel_tag in $FUEL_TAGS; do
    fuel_tag_idx=$((fuel_tag_idx + 1))
    raster2pgsql -s $srid -b $fuel_tag_idx $lcp_file_path $fuel_tag | \
        psql -U gjohnson $database
done

# test/input_data

# lcp_tubbs_fire.lcp
# model_setup.json
# tubbs_1507528800_1507530600.tif
# tubbs_ignition.json

