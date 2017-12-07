package io.pivotal.dil.avroschemadrift;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference:
 * 
 * Types: https://avro.apache.org/docs/1.8.2/spec.html Commons CSV:
 * https://commons.apache.org/proper/commons-csv/user-guide.html Compression:
 * https://avro.apache.org/docs/1.8.2/api/java/org/apache/avro/file/DataFileWriter.html
 * Avro API:
 * https://avro.apache.org/docs/current/gettingstartedjava.html#Serializing+and+deserializing+without+code+generation
 * Avro command line utility:
 * http://www.michael-noll.com/blog/2013/03/17/reading-and-writing-avro-files-from-the-command-line/
 * Download Avro:
 * http://mirror.cc.columbia.edu/pub/software/apache/avro/avro-1.8.2/java/ On
 * versioning:
 * https://stackoverflow.com/questions/12165589/does-avro-schema-evolution-require-access-to-both-old-and-new-schemas
 *
 */

@SpringBootApplication
public class AvroSchemaDriftApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(AvroSchemaDriftApplication.class, args);
	}

	/**
	 * Args:
	 * 
	 * - schema.avsc file - CSV input file (will be read entirely, so each such file
	 * will have the appropriate number of rows) - base name of output file (will
	 * have a numeric value appended to it) - number of rows per file (the batch
	 * size)
	 * 
	 */

	private static final String SCHEMA_ARG = "--schema-file";
	private String schemaFile;
	private static final String CSV_ARG = "--csv-input-file";
	private String csvFile;
	private static final String OUT_FILE_ARG = "--output-file";
	private String outFileBaseName;
	private static final String N_ROWS_ARG = "--batch-size";
	private int nRowsPerBatch;

	private Schema schema; // Avro schema (not the file -- the schema)

	@Override
	public void run(String... args) throws Exception {
		if (args.length < 8) {
			usage();
		}
		for (int i = 0; i < args.length; i += 2) {
			if (SCHEMA_ARG.equals(args[i])) {
				schemaFile = args[i + 1];
			} else if (CSV_ARG.equals(args[i])) {
				csvFile = args[i + 1];
			} else if (OUT_FILE_ARG.equals(args[i])) {
				outFileBaseName = args[i + 1];
			} else if (N_ROWS_ARG.equals(args[i])) {
				nRowsPerBatch = Integer.parseInt(args[i + 1]);
			} else {
				usage();
			}
		}
		System.out.println("\n" + "Avro schema file: " + schemaFile + "\n" + "CSV file (input): " + csvFile + "\n"
				+ "Output file base name: " + outFileBaseName + "\n" + "N rows per batch: " + nRowsPerBatch);

		this.schema = new Schema.Parser().parse(new File(schemaFile));
		List<Field> fieldList = schema.getFields();
		int nFields = fieldList.size();
		List<String> fieldNameList = new ArrayList<>(nFields);
		List<String> fieldTypeNameList = new ArrayList<>(nFields);
		for (Field f : fieldList) {
			fieldNameList.add(f.name());
			String typeName;
			Type type = f.schema().getType();
			if (type.equals(Type.UNION)) {
				typeName = f.schema().getTypes().get(0).getName();
			} else {
				typeName = type.getName();
			}
			fieldTypeNameList.add(typeName);
			System.out.println("Field: " + f.name() + ", type: " + typeName);
		}
		System.out.printf("Namespace: %s, N columns: %d\n\n", schema.getNamespace(), nFields);

		// Open the CSV input file, supporting uncompressed and Gzip compressed files
		// (with ".gz" suffix)
		InputStream fileStream = new FileInputStream(csvFile);
		Reader reader;
		if (csvFile.endsWith(".gz")) {
			reader = new InputStreamReader(new GZIPInputStream(fileStream), "UTF-8");
		} else {
			reader = new InputStreamReader(fileStream);
		}

		Iterable<CSVRecord> records = CSVFormat.RFC4180.parse(reader);
		int nRowsThisFile = 0;
		int chunkNumber = 0;
		DataFileWriter<GenericRecord> dataFileWriter = getDataFileWriter(chunkNumber++);
		GenericRecord row = new GenericData.Record(schema);
		for (CSVRecord record : records) {
			if (nRowsThisFile == this.nRowsPerBatch) {
				dataFileWriter.close();
				dataFileWriter = getDataFileWriter(chunkNumber++);
				nRowsThisFile = 0;
			}
			try {
				for (int i = 0; i < nFields; i++) {
					String colValue = record.get(i);
					// Only add non-null values
					if (colValue != null && colValue.length() > 0) {
						String fieldTypeName = fieldTypeNameList.get(i);
						if ("string".equals(fieldTypeName)) {
							row.put(fieldNameList.get(i), colValue);
						} else if ("boolean".equals(fieldTypeName)) {
							row.put(fieldNameList.get(i), Boolean.valueOf(colValue));
						} else if ("int".equals(fieldTypeName)) {
							row.put(fieldNameList.get(i), Integer.valueOf(colValue));
						} else if ("float".equals(fieldTypeName)) {
							row.put(fieldNameList.get(i), Float.valueOf(colValue));
						} else {
							throw new RuntimeException("Unsupported Avro type: " + fieldTypeName);
						}
					}
				}
				dataFileWriter.append(row);
			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
				System.out.println("ERROR INPUT: " + record.toString());
				throw new RuntimeException(e);
			}
			++nRowsThisFile;
		}

		if (dataFileWriter != null) {
			dataFileWriter.close();
			dataFileWriter = null;
		}
		reader.close();
	}

	// Open a new output file, appending the batch number to the file name
	private DataFileWriter<GenericRecord> getDataFileWriter(int chunkNumber) throws IOException {
		String avroFileName = String.format("%s-%03d.avro", outFileBaseName, chunkNumber);
		System.out.println("Opening new Avro file: " + avroFileName);
		File file = new File(avroFileName);
		DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<GenericRecord>(schema);
		DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<GenericRecord>(datumWriter);
		/*
		 * Codecs:
		 * https://avro.apache.org/docs/1.8.2/api/java/org/apache/avro/file/CodecFactory
		 * .html
		 */
		dataFileWriter.setCodec(CodecFactory.snappyCodec());
		dataFileWriter.create(schema, file);
		return dataFileWriter;
	}

	private void usage() {
		System.err.println("Args: " + SCHEMA_ARG + " file.avsc " + CSV_ARG + " input.csv " + OUT_FILE_ARG
				+ " output_file_name " + N_ROWS_ARG + " <INT_VALUE>");
		System.exit(1);
	}

}
