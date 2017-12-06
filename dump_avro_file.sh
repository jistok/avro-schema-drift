#!/bin/bash

# Set to location of your copy of this JAR
avro_tools_jar="../avro-tools-1.8.2.jar"

java -jar $avro_tools_jar tojson $1

