#!/bin/bash

redis_log=$PWD/redis.log
avro_schema="crimes_v1.avsc"

wget http://download.redis.io/redis-stable.tar.gz
tar xvzf redis-stable.tar.gz
cd redis-stable
make

nohup ./src/redis-server >> $redis_log 2>&1 </dev/null &
echo "Redis PID: $!"

# Enter the initial value, v1, of the schema into Redis
table_name=$( perl -ne 'print "$1\n" if /^\s+"namespace": "([^"]+)",\s*$/;' < $avro_schema )
column_list=$( perl -ne 'print "$1\n" if /^\s+"doc": "([^"]+)",\s*$/;' < $avro_schema )
echo "SET $table_name \"$column_list\"" | ./redis-stable/src/redis-cli

