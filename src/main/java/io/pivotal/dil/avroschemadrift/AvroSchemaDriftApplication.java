package io.pivotal.dil.avroschemadrift;

import java.io.File;

import org.apache.avro.Schema;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference:
 * 
 * Types: https://avro.apache.org/docs/1.8.2/spec.html
 * Compression: https://avro.apache.org/docs/1.8.2/api/java/org/apache/avro/file/DataFileWriter.html
 * Avro API: https://avro.apache.org/docs/current/gettingstartedjava.html#Serializing+and+deserializing+without+code+generation
 * Avro command line utility: http://www.michael-noll.com/blog/2013/03/17/reading-and-writing-avro-files-from-the-command-line/
 * On versioning: https://stackoverflow.com/questions/12165589/does-avro-schema-evolution-require-access-to-both-old-and-new-schemas
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
	 * - schema.avsc file
	 * - CSV input file (will be read entirely, so each such file will have the appropriate number of rows)
	 * - base name of output file (will have a numeric value appended to it)
	 * - number of rows per file (the batch size)
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
		Schema schema = new Schema.Parser().parse(new File(schemaFile));
		System.out.printf("Namespace: %s, N columns: %d\n", schema.getNamespace(), schema.getFields().size());
	}
	
	private void usage() {
		System.err.println("Args: " + SCHEMA_ARG + " file.avsc " + CSV_ARG + " input.csv " + OUT_FILE_ARG
				+ " output_file_name " + N_ROWS_ARG + " <INT_VALUE>");
		System.exit(1);
	}

}
