package fr.pantheonsorbonne.cri;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.languages.JavaJerseyServerCodegen;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.models.OpenAPI;

public class App {

	@Parameter(names = { "--bpmn2-input-dir", "-i" }, description = "path to your bpmn2 file")
	File bpmn2File = new File("target/classes/choreography.xml");

	@Parameter(names = { "--output-directory", "-o" }, description = "a place to store your openapis file")
	File outputDirectory = new File(".");

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

		createOutputDirIfNotThere();

		BPMNFacade diag = new BPMNFacade(this.bpmn2File);

		Map<TParticipant, OpenAPI> map = diag.getOpenApiMap();

		for (Map.Entry<TParticipant, OpenAPI> entry : map.entrySet()) {

			String participantNameEscaped = entry.getKey().getName().replace(' ', '_');
			OpenAPI api = entry.getValue();

			writeJavaServerStubs(participantNameEscaped, api);
		}

		if (writeOpenApiFiles) {
			diag.writeOpenApi(this.outputDirectory).stream()//
					.forEach(f -> LOGGER.info("OpenAPI Spec written: {}", f));

		}

	}

	private void writeJavaServerStubs(String participantNameEscaped, OpenAPI api) {
		ClientOptInput opts = new ClientOptInput();
		AbstractJavaCodegen config = new JavaJerseyServerCodegen();
		config.setOutputDir(
				Paths.get(this.outputDirectory.getPath(), participantNameEscaped, "generated_server_stub").toString());
		opts.config(config);
		opts.openAPI(api);

		new DefaultGenerator().opts(opts).generate();
	}

	private void createOutputDirIfNotThere() {
		try {
			Path outputDir = Paths.get(this.outputDirectory.toString());
			if (!Files.exists(outputDir)) {
				Files.createDirectories(outputDir);
			}
		} catch (IOException e) {
			LOGGER.error("failed to create directory : {}", outputDirectory.toString());
			System.exit(-1);
		}
	}

}
