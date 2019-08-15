package fr.pantheonsorbonne.cri;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

public class App {

	public static <T extends TRootElement> List<T> getRootElements(TDefinitions defs, Class<T> klass) {
		List<T> res  = (List<T>) defs.getRootElement().stream()
				.map(e->e.getValue())
				.filter(obj-> obj.getClass().isAssignableFrom(klass))
				.map(obj-> klass.cast(obj))
				.collect(Collectors.toList());
		
		return res;
	}
	
	
	public static TDefinitions readBPMNFromEmbdedModel(String modelName) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

		StreamSource ss = new StreamSource(App.class.getClassLoader().getResourceAsStream("choreography.xml"));
		JAXBElement<TDefinitions> o = jaxbUnmarshaller.unmarshal(ss, TDefinitions.class);
		return o.getValue();
	}

	public static void main(String[] args) throws JAXBException {

		List<TMessage> defs = getRootElements(readBPMNFromEmbdedModel("choreograph.xml"), TMessage.class);

		defs.stream().map(def -> def.getName()).forEach(System.out::println);

	}
}
