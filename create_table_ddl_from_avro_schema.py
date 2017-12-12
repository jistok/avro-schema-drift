#!/usr/bin/env python

import json
import sys

if len(sys.argv) != 2:
  print 'Usage: %s avro_schema_file' % sys.argv[0]
  sys.exit(1)

schemaFile = sys.argv[1]
with open(schemaFile, 'r') as s:
  jsonStr = s.read()
schema = json.loads(jsonStr)

avroToSqlType = {
	'boolean': 'BOOL',
	'int':     'INT',
	'long':    'BIGINT',
	'float':   'FLOAT4',
	'double':  'FLOAT8',
	'bytes':   'BYTEA',
	'string':  'TEXT'
}

sql = []
distKey = None
for f in schema['fields']:
  fName = f['name']
  if distKey is None:
    distKey = fName
  fType = f['type']
  nullable = False
  if isinstance(fType, (list, tuple)):
    fType = fType[0]
    nullable = True
  sqlType = avroToSqlType[fType]
  clause = fName + '   ' + sqlType
  if not nullable:
    clause += '   ' + 'NOT NULL'
  sql.append(clause)

tableName = schema['namespace']
print 'CREATE TABLE ' + tableName + '\n(\n' + '  ' + ',\n  '.join(sql) + '\n)\n' + 'DISTRIBUTED BY (' + distKey + ');\n'


