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

