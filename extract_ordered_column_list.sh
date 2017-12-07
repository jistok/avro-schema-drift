#!/bin/bash

# This is how to get the '|' separated order list of column names to put into the "doc"
# field of the Avro schema:

perl -e '@cols = (); while (<>) { push @cols, $1 if /^\s+{"name":\s+"([^"]+)",\s+"type":.+$/; } print join("|", @cols) . "\n"; '< crimes_v1.avsc

