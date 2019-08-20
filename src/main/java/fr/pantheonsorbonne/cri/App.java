package fr.pantheonsorbonne.cri;

import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

public class App {

	public static <T extends TRootElement> List<T> getRootElements(TDefinitions defs, Class<T> klass) {
		List<T> res = (List<T>) defs.getRootElement().stream().map(e -> e.getValue())
				.filter(obj -> obj.getClass().isAssignableFrom(klass)).map(obj -> klass.cast(obj))
				.collect(Collectors.toList());

		return res;
	}

	public static void main(String[] args) throws JAXBException, JsonProcessingException {

		BPMNFacade diag = new BPMNFacade("choreograph.xml");

		Content jsonContent = new Content().addMediaType("application/json",
				new MediaType().addEncoding("UTF-8", new Encoding()));
		Info info = new Info().contact(new Contact().email("nicolas.herbaut@univ-paris1.fr").name("Nicolas Herbaut")
				.url("https://nextnet.top")).version("1.0.0");

		
		
//		for (TParticipant participant : diag.getChoreography().getParticipant()) {
//			OpenAPI oai = new OpenAPI().info(info.title(participant.getName()));
//
//			Map<TChoreographyTask, QName> taskMessageMap = diag.getChoreographyTasks().stream()
//					.filter(t -> t.getInitiatingParticipantRef().getLocalPart().contains(participant.getId()))
//					.collect(Collectors.toMap((TChoreographyTask t1) -> t1, (TChoreographyTask t2) -> t2.getMessageFlowRef().get(0)));
//
//			System.out.println(participant.getName() + "\t" + participant.getId());
//			taskMessageMap.entrySet().stream().map(t -> "in " + t.getKey().getName() + " sends " + t.getValue().getLocalPart()).forEach(System.out::println);
//			System.out.println("\n\n");
//		}
		
		
		diag.getChoreography().getMessageFlow().stream()
		.map( m -> diag.getParticipant(m.getSourceRef()).getName()  + " sends " + 
					diag.getMessage(m.getMessageRef()).getName() + " to " + 
					diag.getParticipant(m.getTargetRef()).getName())
		.forEach(System.out::println);

//		for (TChoreographyTask task : diag.getChoreographyTasks()) {
//			OpenAPI oai = new OpenAPI().info(info.title(task.getName()));
//
//			String participant1=task.getParticipantRef().get(0).getLocalPart();
//			String messageFlow1=task.getMessageFlowRef().get(0).getLocalPart();
//			
//			String participant2=task.getParticipantRef().get(1).getLocalPart();
//			String messageFlow2=task.getParticipantRef().get(1).getLocalPart();
//			
//			oai.path("/" + task.getName().replace(" ", "_"),
//					new PathItem().get(new Operation().description("getty").responses(new ApiResponses()
//							.addApiResponse("200", new ApiResponse().description("desc").content(jsonContent)
//
//							))));
//
//			System.out.println(getObjectMapper().writer(new DefaultPrettyPrinter()).writeValueAsString(oai));
//		}

	}

	protected static ObjectMapper getObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();

		mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}
}
