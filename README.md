# Spring Boot Command Line Tool to Write Compressed Avro from CSV Data

This is to support a POC focused on handling _schema drift_.  The idea is that, over the course of
loading the Avro data, the schema changes twice. To support this scenario, there are three different
versions of the Avro schema and three compressed CSV data files.

The process involves building the Spring Boot app, running it against the three versions of the
schema and the three CSV input files.  Each run will create a number of Avro files, which will
be used in the next part of the effort (noticing that the schema has changed and making corresponding
changes to the DDL of the target table).

## Build the Boot app

`./mvnw clean package -DskipTests`

## Run the app three times -- once for each version of the schema

```
for ver in {1..3}
do
  java -jar ./target/avro-schema-drift-0.0.1-SNAPSHOT.jar \
    --schema-file ./crimes_v${ver}.avsc \
    --csv-input-file ./chicago_crimes_10k_part${ver}.csv.gz \
    --output-file /tmp/crimes_v${ver} \
    --batch-size 1000
done
```

That should yield 30 files in /tmp, each containing 1,000 entries.  Next: figure out how we'll load
these Avro files, detecting and reacting to any *schema drift* as we do that.

## Set up Kafka and create a topic

* Clone the "Round Trip" project: `git clone https://github.com/mgoddard-pivotal/gpdb-kafka-round-trip.git`
* `cd ./gpdb-kafka-round-trip/`
* Download Kafka per instructions in this project
* Edit `./kafka_env.sh`
* Start up Zookeeper
* Start up Kafka
* Create the topic (from within the `gpdb-kafka-round-trip` directory):

```
[gpdb-kafka-round-trip]$ topic="crimes_avro"
[gpdb-kafka-round-trip]$ partition_count=8
[gpdb-kafka-round-trip]$ . ./kafka_env.sh
[gpdb-kafka-round-trip]$ $kafka_dir/bin/kafka-topics.sh --create --topic $topic --replication-factor 1 \
>   --partitions $partition_count --zookeeper $zk_host:2181
```

## Build and install the Avro Kafka Go programs
* Clone this repo: `git clone https://github.com/mgoddard-pivotal/confluent-kafka-go.git`
* Build the Avro Kafka producer: `cd ./confluent-kafka-go/examples/avro_producer/ && go build && cp ./avro_producer ~/`
* Install the Redis client: `go get github.com/garyburd/redigo/redis`
* Install the PostgreSQL client: `go get github.com/lib/pq`
* Install this fork of _goavro_: `go get github.com/mgoddard-pivotal/goavro`
* Build the Avro Kafka consumer: `cd ~/confluent-kafka-go/examples/go-kafkacat-avro/ && go build && cp ./go-kafkacat-avro ~/`

## Set up the GPDB tables

* `cd ~/avro-schema-drift/`
* Create the heap table to load: `psql -f create_heap_table.sql`
* Create the external web table, which will consume the Kafka topic: `psql -f create_external_table.sql`

## Install and configure Redis

* `./bootstrap_redis.sh`

## Iterate over the Avro data files, loading into Kafka and loading GPDB

* Verify the table structure:
```
  echo "\\d crimes" | psql
```
* Put the Avro data for the first schema version into Kafka:
```
  $HOME/avro_producer localhost:9092 crimes_avro /tmp/crimes_v1-00?.avro
```
* Load data:
```
  echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql`
```

```
[gpadmin@avro-drift-demo avro-schema-drift]$ ./reset_redis.sh 
Setting the initial value, v1, of the column names in Redis
OK
"id|case_number|crime_date|block|iucr|primary_type|description|location_desc|arrest|domestic|beat|district|ward|community_area|fbi_code|x_coord|y_coord"
OK
Done
[gpadmin@avro-drift-demo avro-schema-drift]$ psql -f create_heap_table.sql 
Timing is on.
DROP TABLE
Time: 151.128 ms
CREATE TABLE
Time: 118.571 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ psql -f ./create_external_table.sql 
Timing is on.
DROP EXTERNAL TABLE
Time: 65.840 ms
CREATE EXTERNAL TABLE
Time: 76.937 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ $HOME/avro_producer localhost:9092 crimes_avro /tmp/crimes_v1-00?.avro
Created Producer rdkafka#producer-1
Reading Avro file /tmp/crimes_v1-000.avro now ...
Reading Avro file /tmp/crimes_v1-001.avro now ...
Reading Avro file /tmp/crimes_v1-002.avro now ...
Reading Avro file /tmp/crimes_v1-003.avro now ...
Reading Avro file /tmp/crimes_v1-004.avro now ...
Reading Avro file /tmp/crimes_v1-005.avro now ...
Reading Avro file /tmp/crimes_v1-006.avro now ...
Reading Avro file /tmp/crimes_v1-007.avro now ...
Reading Avro file /tmp/crimes_v1-008.avro now ...
Reading Avro file /tmp/crimes_v1-009.avro now ...
[gpadmin@avro-drift-demo avro-schema-drift]$ echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
Timing is on.
INSERT 0 10000
Time: 3199.418 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
Timing is on.
INSERT 0 0
Time: 3768.138 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ $HOME/avro_producer localhost:9092 crimes_avro /tmp/crimes_v2-00?.avro
Created Producer rdkafka#producer-1
Reading Avro file /tmp/crimes_v2-000.avro now ...
Reading Avro file /tmp/crimes_v2-001.avro now ...
Reading Avro file /tmp/crimes_v2-002.avro now ...
Reading Avro file /tmp/crimes_v2-003.avro now ...
Reading Avro file /tmp/crimes_v2-004.avro now ...
Reading Avro file /tmp/crimes_v2-005.avro now ...
Reading Avro file /tmp/crimes_v2-006.avro now ...
Reading Avro file /tmp/crimes_v2-007.avro now ...
Reading Avro file /tmp/crimes_v2-008.avro now ...
Reading Avro file /tmp/crimes_v2-009.avro now ...
[gpadmin@avro-drift-demo avro-schema-drift]$ echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
Timing is on.
INSERT 0 0
Time: 2154.567 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ ~/ddl-executor public.crimes
GP_MASTER_HOST: avro-drift-demo
GP_MASTER_PORT: 5432
GP_DATABASE: gpadmin
Connected to GPDB (host: avro-drift-demo, port: 5432, DB: gpadmin)
DDL: ALTER TABLE public.crimes ADD COLUMN crime_year INT, ADD COLUMN record_update_date TEXT
SUCCESS
DDL: ALTER EXTERNAL TABLE public.crimes_kafka ADD COLUMN crime_year INT, ADD COLUMN record_update_date TEXT
SUCCESS
Redis: SET public.crimes "id|case_number|crime_date|block|iucr|primary_type|description|location_desc|arrest|domestic|beat|district|ward|community_area|fbi_code|x_coord|y_coord|crime_year|record_update_date"
SUCCEEDED
SUCCESS deleting DDL key "public.crimes-DDL" from Redis
All done
[gpadmin@avro-drift-demo avro-schema-drift]$ echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
Timing is on.
INSERT 0 10000
Time: 718.363 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
Timing is on.
INSERT 0 0
Time: 3897.438 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ $HOME/avro_producer localhost:9092 crimes_avro /tmp/crimes_v3-00?.avro
Created Producer rdkafka#producer-1
Reading Avro file /tmp/crimes_v3-000.avro now ...
Reading Avro file /tmp/crimes_v3-001.avro now ...
Reading Avro file /tmp/crimes_v3-002.avro now ...
Reading Avro file /tmp/crimes_v3-003.avro now ...
Reading Avro file /tmp/crimes_v3-004.avro now ...
Reading Avro file /tmp/crimes_v3-005.avro now ...
Reading Avro file /tmp/crimes_v3-006.avro now ...
Reading Avro file /tmp/crimes_v3-007.avro now ...
Reading Avro file /tmp/crimes_v3-008.avro now ...
Reading Avro file /tmp/crimes_v3-009.avro now ...
[gpadmin@avro-drift-demo avro-schema-drift]$ echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
Timing is on.
INSERT 0 0
Time: 115.780 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ ~/ddl-executor public.crimes
GP_MASTER_HOST: avro-drift-demo
GP_MASTER_PORT: 5432
GP_DATABASE: gpadmin
Connected to GPDB (host: avro-drift-demo, port: 5432, DB: gpadmin)
DDL: ALTER TABLE public.crimes ADD COLUMN latitude FLOAT4, ADD COLUMN longitude FLOAT4, ADD COLUMN location TEXT
SUCCESS
DDL: ALTER EXTERNAL TABLE public.crimes_kafka ADD COLUMN latitude FLOAT4, ADD COLUMN longitude FLOAT4, ADD COLUMN location TEXT
SUCCESS
Redis: SET public.crimes "id|case_number|crime_date|block|iucr|primary_type|description|location_desc|arrest|domestic|beat|district|ward|community_area|fbi_code|x_coord|y_coord|crime_year|record_update_date|latitude|longitude|location"
SUCCEEDED
SUCCESS deleting DDL key "public.crimes-DDL" from Redis
All done
[gpadmin@avro-drift-demo avro-schema-drift]$ echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
Timing is on.
INSERT 0 10000
Time: 2806.974 ms
[gpadmin@avro-drift-demo avro-schema-drift]$ echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
Timing is on.
INSERT 0 0
Time: 4496.397 ms
```

