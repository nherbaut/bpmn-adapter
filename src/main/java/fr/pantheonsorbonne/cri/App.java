package fr.pantheonsorbonne.cri;

import java.io.File;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.models.OpenAPI;

public class App {

	@Parameter(names = { "--bpmn2-input-dir", "-i" },description = "path to your bpmn2 file")
	File bpmn2File=new File("target/classes/choreography.xml");
	
	@Parameter(names = { "--output-directory", "-o" },description = "a place to store your openapis file")
	File outputDirectory=new File(".");

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) throws JAXBException, JsonProcessingException {

		App app = new App();
		JCommander.newBuilder()
		  .addObject(app)
		  .build()
		  .parse(args);
		app.run();
		

	}
	
	public void run() {
		BPMNFacade diag = new BPMNFacade(this.bpmn2File);

		Map<TParticipant, OpenAPI> map = diag.getOpenApiMap();
		diag.writeOpenApi(this.outputDirectory);
	}

}
