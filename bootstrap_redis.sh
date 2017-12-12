#!/bin/bash

redis_log=$PWD/redis.log
avro_schema="crimes_v1.avsc"
redis_cli="./redis-stable/src/redis-cli"

echo "Downloading and building Redis"
curl http://download.redis.io/redis-stable.tar.gz | tar xzvf -
cd redis-stable
make

echo "Starting Redis server"
nohup ./src/redis-server >> $redis_log 2>&1 </dev/null &
echo "Redis PID: $!"
sleep 1
cd -

read -p "Press enter to continue"

echo "Setting the initial value, v1, of the column names in Redis"
table_name=$( perl -ne 'print "$1\n" if /^\s+"namespace": "([^"]+)",\s*$/;' < $avro_schema )
column_list=$( perl -ne 'print "$1\n" if /^\s+"doc": "([^"]+)",\s*$/;' < $avro_schema )
echo "SET $table_name \"$column_list\"" | $redis_cli
echo "GET $table_name" | $redis_cli

echo "Done"

