package io.pivotal.dil.avroschemadrift;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference:
 * Compression: https://avro.apache.org/docs/1.8.2/api/java/org/apache/avro/file/DataFileWriter.html
 * Avro API: https://avro.apache.org/docs/current/gettingstartedjava.html#Serializing+and+deserializing+without+code+generation
 * Avro command line utility: http://www.michael-noll.com/blog/2013/03/17/reading-and-writing-avro-files-from-the-command-line/
 *
 */

@SpringBootApplication
public class AvroSchemaDriftApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(AvroSchemaDriftApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		//if (args.length > 0 && args[0].equals("exitcode")) {}
		for (String arg: args) {
			System.out.println("arg: \"" + arg + "\"");
		}
	}

}
