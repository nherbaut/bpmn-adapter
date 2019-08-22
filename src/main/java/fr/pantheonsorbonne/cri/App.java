package fr.pantheonsorbonne.cri;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

public class App {

	public static <T extends TRootElement> List<T> getRootElements(TDefinitions defs, Class<T> klass) {
		List<T> res = (List<T>) defs.getRootElement().stream().map(e -> e.getValue())
				.filter(obj -> obj.getClass().isAssignableFrom(klass)).map(obj -> klass.cast(obj))
				.collect(Collectors.toList());

		return res;
	}

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(App.class);

	class MessageTypeDTO {
		public MessageTypeDTO(TMessage message, Class<?> klass) {
			this.message = message;
			this.klass = klass;
		}

		public TMessage message;
		public Class<?> klass;
	}

	public static void main(String[] args) throws JAXBException, JsonProcessingException {

		File bpmnFile = new File("target/classes/choreography.xml");

		BPMNFacade diag = new BPMNFacade(bpmnFile);

		Content jsonContent = new Content().addMediaType("application/json",
				new MediaType().addEncoding("UTF-8", new Encoding()));
		Info info = new Info().contact(new Contact().email("nicolas.herbaut@univ-paris1.fr").name("Nicolas Herbaut")
				.url("https://nextnet.top")).version("1.0.0");

		for (TParticipant participant : diag.getChoreography().getParticipant()) {
			LOGGER.debug("for participant {}", participant.getName());
			OpenAPI oai = new OpenAPI().info(info.title(participant.getName()));
			List<TChoreographyTask> tasksWithIncommingMessages = diag.getChoreographyTasks().stream()
					.filter(t -> !t.getInitiatingParticipantRef().getLocalPart().equals(participant.getName()))
					.collect(Collectors.toList());

			io.swagger.v3.oas.models.Paths paths = new io.swagger.v3.oas.models.Paths();
			oai.paths(paths);
			for (TChoreographyTask task : tasksWithIncommingMessages) {
				LOGGER.debug("for incomming task {}", task.getName());

				String endpointName = "/" + prettyEndpoint(task.getName());

				List<Class<?>> klasses = task.getMessageFlowRef().stream()
						.map(m -> diag.getMessage(diag.getMessageFlow(m).getMessageRef()))
						.map(m -> diag.getTypeFromMessage(m)).collect(Collectors.toList());

				Operation op = new Operation();
				for (QName m : task.getMessageFlowRef()) {

					PathItem item = new PathItem();
					paths.addPathItem(endpointName, item);
					item.description(task.getName());

					if (klasses.size() == 1) {

						ApiResponse response = new ApiResponse()
								.$ref("#/components/schemas/" + klasses.get(0).getName());
						op.responses(new ApiResponses().addApiResponse("200", response));
						item.get(op);
					} else {
						RequestBody request = new RequestBody().required(true)
								.content(new Content().addMediaType("application/json", new MediaType().schema(
										new Schema().$ref("#/components/schemas/" + klasses.get(0).getName()))));
						op.requestBody(request);
						ApiResponse response = new ApiResponse()
								.$ref("#/components/schemas/" + klasses.get(1).getName());
						op.responses(new ApiResponses().addApiResponse("200", response));
						item.post(op);
					}

					

				}

			}

			oai.components(diag.getComponents());

			try (FileWriter writer = new FileWriter(new File(participant.getName() + ".yaml"))) {
				writer.write(toYaml(oai));
				LOGGER.debug("file {} written", participant.getName() + ".yaml");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

		}

//		diag.getChoreography().getMessageFlow().stream()
//				.map(m -> diag.getParticipant(m.getSourceRef()).getName() + " sends "
//						+ diag.getMessage(m.getMessageRef()).getName() + " to "
//						+ diag.getParticipant(m.getTargetRef()).getName())
//				.forEach(System.out::println);

	}

	private static String toYaml(OpenAPI api) {
		try {
			String oaiString = getObjectMapper().writer(new DefaultPrettyPrinter()).writeValueAsString(api);
			JsonNode jsonNodeTree = new ObjectMapper().readTree(oaiString);

			return new YAMLMapper().writeValueAsString(jsonNodeTree);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	private static String capitalize(String str) {
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	private static String prettyEndpoint(String s) {
		return Arrays.stream(s.split(" ")).peek(App::capitalize).collect(Collectors.joining());
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
