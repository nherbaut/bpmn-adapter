package fr.pantheonsorbonne.cri;

import static org.junit.Assert.assertNotNull;

import javax.xml.bind.JAXBException;

import org.junit.Test;

public class AppTest {

	@Test
	public void shouldAnswerWithTrue() throws JAXBException {

		assertNotNull("failed to unmarshall domain object",App.readBPMNFromEmbdedModel("choreography.xml"));

		

	}
}
