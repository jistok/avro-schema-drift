DROP EXTERNAL TABLE IF EXISTS public.crimes_kafka;
CREATE EXTERNAL WEB TABLE public.crimes_kafka
(LIKE public.crimes)
EXECUTE '$HOME/go-kafkacat-avro --avro=true --broker=localhost:9092 consume --group=GPDB_Consumer_Group crimes_avro --eof 2>>/tmp/`printf "kafka_consumer_%02d.log" $GP_SEGMENT_ID`'
ON ALL FORMAT 'CSV' (DELIMITER ',' NULL '')
LOG ERRORS SEGMENT REJECT LIMIT 1 PERCENT;

