# Handling Schema Drift (or, evolution) With Avro Data and Greenplum Database (GPDB)

This is to support a POC focused on handling _schema drift_.  The idea is that, over the course of
time, the Avro formatted data changes, with new fields being added.  Avro files contain the schema
for the data, so we can determine when schema changes happen if we compare the schema for each of
the Avro files being processed to the current schema.  Since the ultimate destination for this
data is a table in GPDB, any time the Avro schema changes, those changes need to be
made in the target table.  This is what we seek to explore here with this demo. Additionally, the
data transport layer we'll use will be Kafka, with each of the Avro files being loaded into a Kafka
topic and consumed using _external web tables_ in GPDB.

## Use this Spring Boot command line tool to write compressed Avro from CSV data

The process involves building the Spring Boot app, running it against the three versions of the
schema and the three CSV input files.  Each run will create a number of Avro files, which will
be used in the next part of the effort.

* Build the app: `./mvnw clean package -DskipTests`
* Run the app once for each of the three Avro schema variants:
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
This should yield 30 files in /tmp, each containing 1,000 entries.

## Set up Kafka and create a topic

* Clone the "GPDB Kafka Round Trip" project: `git clone https://github.com/mgoddard-pivotal/gpdb-kafka-round-trip.git`
* `cd ./gpdb-kafka-round-trip/`
* Download Kafka per instructions in this project
* Edit `./kafka_env.sh`
* Start up Zookeeper
* Start up Kafka
* Create the `crimes_avro` topic:
```
[gpdb-kafka-round-trip]$ topic="crimes_avro"
[gpdb-kafka-round-trip]$ partition_count=256
[gpdb-kafka-round-trip]$ . ./kafka_env.sh
[gpdb-kafka-round-trip]$ $kafka_dir/bin/kafka-topics.sh --create --topic $topic --replication-factor 1 \
>   --partitions $partition_count --zookeeper $zk_host:2181
```

## Build and install the Avro Kafka Go programs

* Clone this repo: `git clone https://github.com/mgoddard-pivotal/confluent-kafka-go.git`
* Build and install the Avro Kafka producer: `cd ./confluent-kafka-go/examples/avro_producer/ && go build && cp ./avro_producer ~/ && cd -`
* Install the Redis client: `go get github.com/garyburd/redigo/redis`
* Install the PostgreSQL client: `go get github.com/lib/pq`
* Install this fork of _goavro_: `go get github.com/mgoddard-pivotal/goavro`
* Build and install the Avro Kafka consumer: `cd ~/confluent-kafka-go/examples/go-kafkacat-avro/ && go build && cp ./go-kafkacat-avro ~/ && cd -`
* Build and install the DDL executor program: `cd ~/confluent-kafka-go/examples/ddl-executor/ && go build && cp ./ddl-executor ~/ && cd -`

## Set up the GPDB tables

We use the external web table, the second table created here, to run the `go-kafkacat-avro` binary which consumes
Avro data from the Kafka topic, checks the schema for each Avro file it encounters, and ensures that its list of
fields is equal to, or a subset of, those currently defined for the GPDB table (we store this in Redis, keyed by
the schema and table name).  If this is not the case, a flag is set in Redis for the current GPDB transaction ID
(`GP_XID`), so that any running parallel load processes will stop.  Next, an `ALTER TABLE ...` DDL expression is 
generated and placed into Redis, and this instance of `go-kafkacat-avro` stops running, terminating the current
load process.  Prior to each load process, _DDL executor_ (`ddl-executor`) is run to run any DDL generated by the
previous load process.  We envision running this periodically, likely via _cron_.

* `cd ~/avro-schema-drift/`
* Create the `gpadmin` database, if it doesn't yet exist: `createdb gpadmin`
* Create the heap table, the destination for the data: `psql -f create_heap_table.sql`
* Create the external web table, which will consume from the Kafka topic: `psql -f create_external_table.sql`

## Install and configure Redis

The last step of this process is to do a `SET` in Redis for the `public.crimes` key, where the value reflects
the initial version, `v1`, of the Avro schema and corresponding tables, just created (above).

* `./bootstrap_redis.sh`

## On the GPDB master host, logged in as the _gpadmin_ user, iterate over the three Avro schema variants


* Run the DDL executor:
```
  $HOME/ddl-executor public.crimes
```

* Verify the table structure:
```
  echo "\\d crimes" | psql
```

* Dump the Avro data for the appropriate schema version into the Kafka topic:

To change the schema version, change the `v1` to `v2`, and then `v3`.

```
  $HOME/avro_producer localhost:9092 crimes_avro /tmp/crimes_v1-00?.avro
```

* Load data:
```
  echo "INSERT INTO crimes SELECT * FROM crimes_kafka" | psql
```

* At the end, reset the data in Redis and recreate the tables in GPDB:
```
  ./reset_redis.sh
```

