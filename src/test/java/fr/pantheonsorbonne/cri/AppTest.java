package fr.pantheonsorbonne.cri;

import static org.junit.Assert.assertTrue;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;

import org.junit.Test;

public class AppTest {

	@Test
	public void shouldAnswerWithTrue() throws JAXBException {

		JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		
		
		StreamSource ss = new StreamSource(App.class.getClassLoader().getResourceAsStream("choreography.xml"));
		JAXBElement<TDefinitions> o =  jaxbUnmarshaller.unmarshal(ss,TDefinitions.class);

		assertTrue("we don't unmarshall domain object",
				o.getValue().getClass().getName().equals("fr.pantheonsorbonne.cri.TDefinitions"));

	}
}
