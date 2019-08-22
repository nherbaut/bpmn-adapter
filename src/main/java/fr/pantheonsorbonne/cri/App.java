package fr.pantheonsorbonne.cri;

import java.io.File;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.models.OpenAPI;

public class App {

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) throws JAXBException, JsonProcessingException {

		File bpmnFile = new File("target/classes/choreography.xml");

		BPMNFacade diag = new BPMNFacade(bpmnFile);
		
		Map<TParticipant, OpenAPI> map = diag.getOpenApiMap();
		diag.writeOpenApi();

		

	}

	

}
