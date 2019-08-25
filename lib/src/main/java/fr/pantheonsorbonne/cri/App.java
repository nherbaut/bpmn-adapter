package fr.pantheonsorbonne.cri;

import java.io.File;

import javax.xml.bind.JAXBException;

import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;

public class App {

	@Parameter(names = { "--bpmn2-input-dir", "-i" }, description = "path to your bpmn2 file")
	File bpmn2File = new File("target/classes/choreography.bpmn2");

	@Parameter(names = { "--output-directory", "-o" }, description = "a place to store your openapis file")
	File outputDirectory = new File("target/openapi");

	@Parameter(names = { "--write-openapi-files", "-w" }, description = "generate the openapi yaml specs on disk")
	boolean writeOpenApiFiles = false;

	@Parameter(names = "--help", help = true)
	private boolean help = false;

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) throws JAXBException, JsonProcessingException {

		App app = new App();
		JCommander commander = JCommander.newBuilder().addObject(app).build();
		commander.parse(args);
		if (app.help) {
			commander.usage();
			return;
		}
		app.run();

	}

	public void run() {

		

		BPMNFacade diag = new BPMNFacade(this.bpmn2File);

		diag.writeOpenAPIArtefacts(outputDirectory);

		if (writeOpenApiFiles) {
			diag.writeOpenApi(this.outputDirectory).stream()//
					.forEach(f -> LOGGER.info("OpenAPI Spec written: {}", f));

		}

	}

}
