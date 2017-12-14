#!/bin/bash

avro_schema="crimes_v1.avsc"
redis_cli="./redis-stable/src/redis-cli"

echo "Setting the initial value, v1, of the column names in Redis"
table_name=$( perl -ne 'print "$1\n" if /^\s+"namespace": "([^"]+)",\s*$/;' < $avro_schema )
column_list=$( perl -ne 'print "$1\n" if /^\s+"doc": "([^"]+)",\s*$/;' < $avro_schema )
echo "SET $table_name \"$column_list\"" | $redis_cli
echo "GET $table_name" | $redis_cli
echo "CONFIG SET protected-mode no" | $redis_cli

echo "Done"

