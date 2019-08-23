package fr.pantheonsorbonne.cri;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.xsd.ecore.XSDEcoreBuilder;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.languages.JavaJerseyServerCodegen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.factories.SchemaFactoryWrapper;
import com.fasterxml.jackson.module.jsonSchema.factories.VisitorContext;
import com.fasterxml.jackson.module.jsonSchema.factories.WrapperFactory;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

public class BPMNFacade {

	private static final Logger LOGGER = LoggerFactory.getLogger(BPMNFacade.class);
	private ClassLoader privateClassLoader = new ByteArrayClassLoader(this.getClass().getClassLoader(), Collections.EMPTY_MAP);
	private static final String BPMN2_DEFINITIONS = "bpmn2:definitions";
	private final TDefinitions definitions;
	/**
	 * Contains the namespace for external types of the BPMN2 file
	 */
	private final Map<String, String> ns = new HashMap<String, String>();

	/**
	 * Contains the Dynamically created types from the linked XSD
	 */
	private final Map<String, Collection<Class<?>>> types = new HashMap<>();

	/**
	 * Allow Deserializing the shemas from the provided API class
	 * 
	 * @author nherbaut
	 *
	 */
	class OpenAPIDeserializer2 extends OpenAPIDeserializer {
		public Schema<?> getSchema(ObjectNode node, String location) {
			ParseResult pr = new ParseResult();

			Schema<?> res = getSchema(node, location, pr);
			if (!pr.isValid()) {
				throw new RuntimeException(pr.getMessages().stream().collect(Collectors.joining()));
			}
			return res;

		}

	}

	private static class IgnoreURNSchemaFactoryWrapper extends SchemaFactoryWrapper {
		public IgnoreURNSchemaFactoryWrapper() {
			this(null, new WrapperFactory());
		}

//		public IgnoreURNSchemaFactoryWrapper(SerializerProvider p) {
//			this(p, new WrapperFactory());
//		}
//
//		protected IgnoreURNSchemaFactoryWrapper(WrapperFactory wrapperFactory) {
//			this(null, wrapperFactory);
//		}

		public IgnoreURNSchemaFactoryWrapper(SerializerProvider p, WrapperFactory wrapperFactory) {
			super(p, wrapperFactory);
			visitorContext = new VisitorContext() {
				public String javaTypeToUrn(JavaType jt) {
					return null;
				}
			};
		}
	}

	private Components getComponents() {
		Components components = new Components();
		ObjectMapper mapper = new ObjectMapper();
		IgnoreURNSchemaFactoryWrapper visitor = new IgnoreURNSchemaFactoryWrapper();

		try {

			for (Map.Entry<String, Collection<Class<?>>> type : this.types.entrySet()) {
				for (Class<?> klass : type.getValue()) {

					// create a json schema from this class
					mapper.acceptJsonFormatVisitor(klass, visitor);
					JsonSchema jsonSchema = visitor.finalSchema();

					String jsonSchemaStr = mapper.writer().writeValueAsString(jsonSchema);

					// parse the schema as json
					ObjectNode objectNode = new ObjectMapper().readValue(jsonSchemaStr, ObjectNode.class);

					// use the unsealed Deserializer to generate the schema
					Schema<?> schema = new OpenAPIDeserializer2().getSchema(objectNode, "");
					components.addSchemas(klass.getName(), schema);

				}

			}

		} catch (IOException e) {
			System.out.println(e);
		}

		return components;

	}

	private TDefinitions readBPMNFromEmbdedModel(File bpmnFile) {
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

			StreamSource ss = new StreamSource(bpmnFile);
			JAXBElement<TDefinitions> o = jaxbUnmarshaller.unmarshal(ss, TDefinitions.class);

			SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

			DefaultHandler handler = new DefaultHandler() {
				@Override
				public void startElement(String uri, String localName, String qName, Attributes attributes)
						throws SAXException {
					if (BPMN2_DEFINITIONS.equals(qName)) {

						for (int i = 0; i < attributes.getLength(); i++) {
							if (attributes.getQName(i).contains(":")) {
								ns.put(attributes.getValue(i), attributes.getQName(i).split(":")[1]);
							}
						}
					}

				}
			};

			saxParser.parse(bpmnFile, handler);

			return o.getValue();
		} catch (JAXBException | ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private TChoreography getChoreography() {
		return getDefinitionElements(TChoreography.class).findFirst().get();

	}

	public BPMNFacade(File bpmnFile) {

		this.definitions = readBPMNFromEmbdedModel(bpmnFile);

		this.definitions.getImport().stream() //
				.filter(i -> i.getLocation().equals(i.getLocation())) //
				.forEach(i -> types.put(i.getNamespace(),
						getExternalTypes(Paths.get(bpmnFile.getParentFile().getPath(), i.getLocation()).toFile())));

	}

	private List<TChoreographyTask> getChoreographyTasks() {
		return this.getChoreography().getFlowElement().stream() //
				.map(e -> e.getValue()) //
				.filter(obj -> obj instanceof TChoreographyTask).map(obj -> (TChoreographyTask) obj) //
				.collect(Collectors.toList());
	}

	private <T extends TBaseElement> Stream<T> getDefinitionElements(Class<T> klass) {
		return definitions.getRootElement().stream()//
				.map(e -> e.getValue()) //
				.filter(e -> klass.isInstance(e))//
				.map(e -> klass.cast(e));
	}

	private TMessage getMessage(QName qname) {
		if (qname == null) {
			return new TMessage();
		}
		return this.getDefinitionElements(TMessage.class)//
				.filter(m -> qname.getLocalPart().equals("#" + m.getId()) || qname.getLocalPart().equals(m.getId()))//
				.findFirst()//
				// .orElseThrow(() -> new NoSuchElementException(qname.toString()));
				.orElse(null);
	}

	private TMessageFlow getMessageFlow(QName qname) {
		if (qname == null) {
			return new TMessageFlow();
		}
		return this.getChoreography().getMessageFlow().stream()//
				.filter(m -> qname.getLocalPart().equals("#" + m.getId()) || qname.getLocalPart().equals(m.getId()))//
				.findFirst()//
				// .orElseThrow(() -> new NoSuchElementException(qname.toString()));
				.orElse(null);
	}

	private TItemDefinition getItemDef(QName qname) {
		if (qname == null) {
			return new TItemDefinition();
		}
		return this.getDefinitionElements(TItemDefinition.class)//
				.filter(m -> qname.getLocalPart().equals("#" + m.getId()) || qname.getLocalPart().equals(m.getId()))//
				.findFirst()//
				// .orElseThrow(() -> new NoSuchElementException(qname.toString()));
				.orElse(null);
	}

	private static final String GENERATED_PACKAGE = "";
	private static final AnnotationDescription REQUIRED_FIELD = AnnotationDescription.Builder.ofType(JsonProperty.class)
			.define("value", "").define("required", true).build();

	private Collection<Class<?>> getExternalTypes(File path) {

		Collection<Class<?>> res = new ArrayList<Class<?>>();
		URI schemaURI = URI.createFileURI(path.getPath());
		XSDEcoreBuilder xsdEcoreBuilder = new XSDEcoreBuilder();

		Collection<EObject> eCorePackages = xsdEcoreBuilder.generate(schemaURI);
		Deque<EClassifier> xsdKlasses = new ArrayDeque<EClassifier>(
				eCorePackages.stream().findFirst().get().eContents().stream()//
						.map(o -> (EClassifier) o)//
						.collect(Collectors.toList()));

		ByteBuddy bb = new ByteBuddy();
		typeBuilder: while (!xsdKlasses.isEmpty()) {

			LOGGER.debug("### " + xsdKlasses.size() + " to GO!");

			EClassifier klassifier = xsdKlasses.poll();
			if (klassifier.getName().equals("DocumentRoot")) {
				continue;
			}
			LOGGER.debug("trying to generate dynamic type for " + klassifier.getName());
			DynamicType.Builder<?> typeBuilder = null;

			if (klassifier instanceof EClass) {
				typeBuilder = bb.subclass(Object.class);
				EClass klass = (EClass) klassifier;
				for (int i = 0; i < klass.getFeatureCount(); i++) {
					ETypedElement feature = klass.getEStructuralFeature(i);
					if (feature.getName().equals("group")) {
						continue;
					}
					Class<?> attributeClass = feature.getEType().getInstanceClass();

					if (attributeClass == null) {
						// can be a complex type, not sure if defined...
						try {
							attributeClass = Class.forName(GENERATED_PACKAGE + feature.getEType().getName(),true,this.privateClassLoader);
						} catch (ClassNotFoundException e) {
							attributeClass = null;
						}

						if (attributeClass == null) {
							// type not already defined in the classloader
							LOGGER.debug("missing type" + feature.getEType().getName()
									+ ", deferring complex type creation");
							xsdKlasses.addLast(klassifier);
							continue typeBuilder;
						}
					}
					String attributeName = feature.getName();

					if (feature.getUpperBound() != 1) {
						// List
						Generic attributeList = TypeDescription.Generic.Builder//
								.parameterizedType(List.class, attributeClass)//
								.build();
						typeBuilder = typeBuilder//
								.defineField(attributeName, attributeList,
										net.bytebuddy.description.modifier.Visibility.PUBLIC)//
								.annotateField(REQUIRED_FIELD);

					} else {
						// scalar
						typeBuilder = typeBuilder//
								.defineField(attributeName, attributeClass,
										net.bytebuddy.description.modifier.Visibility.PUBLIC)//
								.annotateField(REQUIRED_FIELD);
					}

				}
			} else if (klassifier instanceof EEnum) {
				EEnum eenum = (EEnum) klassifier;
				typeBuilder = bb.makeEnumeration(eenum.getELiterals().stream()//
						.map(e -> e.getName())//
						.collect(Collectors.toSet()));
			} else {
				LOGGER.debug("don't know what to do with this" + klassifier + " [DROPING]");
				continue typeBuilder;
			}

			typeBuilder = typeBuilder.name(GENERATED_PACKAGE + klassifier.getName());
			Class<?> xsdType = typeBuilder.make()
					.load(privateClassLoader, ClassLoadingStrategy.Default.INJECTION).getLoaded();
			res.add(xsdType);

		}

		return res;
	}

	private Class<?> getTypeFromMessage(TMessage message) {

		TItemDefinition itemDef = this.getItemDef(message.getItemRef());
		if (itemDef == null || itemDef.getStructureRef() == null) {
			LOGGER.warn("Message {} has no type, falling back on the default Type", message.getName());
			return Object.class;
		}
		String xsdName = itemDef.getStructureRef().getLocalPart();

		Collection<Class<?>> klasses = this.types
				.get(this.getItemDef(message.getItemRef()).getStructureRef().getNamespaceURI());
		return klasses.stream()
				// .peek(System.out::println)
				// comparison is done ignore the case, since dynamic classes are capitalized
				.filter(k -> k.getName().equalsIgnoreCase(GENERATED_PACKAGE + xsdName))//
				.findAny()//
				.orElse(null);
	}

	public Map<TParticipant, OpenAPI> getOpenApiMap() {
		Map<TParticipant, OpenAPI> res = new HashMap<TParticipant, OpenAPI>();

		for (TParticipant participant : this.getChoreography().getParticipant()) {
			String participantName = participant.getName();
			LOGGER.trace("for participant {}", participantName);
			OpenAPI oai = getDefaultOAI(participantName);
			List<TChoreographyTask> tasksWithIncommingMessages = getTaskWithIncommingMessageForParticipant(
					participant.getId());

			io.swagger.v3.oas.models.Paths paths = new io.swagger.v3.oas.models.Paths();
			oai.paths(paths);
			for (TChoreographyTask task : tasksWithIncommingMessages) {
				String taskName = task.getName();
				LOGGER.trace("for incoming task {}", taskName);

				List<Class<?>> klasses = getMessageTypes(task);

				Class<?> incommingMessageType = null;
				Class<?> outGoingMessageType = null;

				incommingMessageType = klasses.get(0);

				if (klasses.size() == 2) {

					outGoingMessageType = klasses.get(1);

				} else {

					Optional<TChoreographyTask> dual = this.findDualTask(task);
					// we need to find which task is the dual for this one
					if (dual.isPresent()) {

						outGoingMessageType = getMessageTypes(dual.get()).get(0);
					} else {
						LOGGER.warn("Fail to generate proper conversation in {} ", taskName);
					}

				}

				populatePath(paths, taskName, incommingMessageType, outGoingMessageType);

			}
			oai.components(this.getComponents());
			res.put(participant, oai);

		}
		return res;
	}

	private Optional<TChoreographyTask> findDualTask(TChoreographyTask task) {
		return this.getChoreographyTasks().stream() //
				.filter(t -> t.getMessageFlowRef().size() == 1)//
				.filter(t -> t.getParticipantRef().size() == 2)//
				.filter(t -> t.getParticipantRef().get(0).equals(task.getParticipantRef().get(1))
						&& t.getParticipantRef().get(1).equals(task.getParticipantRef().get(0)))//
				.findAny();

	}

	private void populatePath(io.swagger.v3.oas.models.Paths paths, String taskName, Class<?> incommingMessageType,
			Class<?> outGoingMessageType) {
		String endpointName = "/" + prettyEndpoint(taskName);
		Operation op = new Operation();
		PathItem item = new PathItem();
		paths.addPathItem(endpointName, item);
		item.description(taskName);

		if (incommingMessageType != null) {
			addRequest(op, incommingMessageType);
		}
		if (outGoingMessageType != null) {
			addResponse(op, item, outGoingMessageType);
		}
	}

	private List<Class<?>> getMessageTypes(TChoreographyTask task) {
		return task.getMessageFlowRef().stream()//
				.map(m -> this.getMessage(this.getMessageFlow(m).getMessageRef()))//
				.map(m -> this.getTypeFromMessage(m))//
				.collect(Collectors.toList());
	}

	private List<TChoreographyTask> getTaskWithIncommingMessageForParticipant(String participantName) {
		return this.getChoreographyTasks().stream()//
				.filter(t -> !t.getInitiatingParticipantRef().getLocalPart().equals("#" + participantName)
						&& !t.getInitiatingParticipantRef().getLocalPart().equals(participantName))//
				.filter(t -> t.getParticipantRef().stream()//
						.map(q -> q.getLocalPart())//
						// .peek(System.out::println)//
						.collect(Collectors.toSet()).contains("#" + participantName))
				.collect(Collectors.toList());
	}

	private OpenAPI getDefaultOAI(String participantName) {
		return new OpenAPI()//
				.info(getDefaultInfo()//
						.title(participantName));
	}

	private Info getDefaultInfo() {
		return new Info()//
				.contact(new Contact()//
						.email("nicolas.herbaut@univ-paris1.fr")//
						.name("Nicolas Herbaut")//
						.url("https://nextnet.top"))
				.version("1.0.0");
	}

	private void addResponse(Operation op, PathItem item, Class<?> outGoingMessageType) {
		ApiResponse response = new ApiResponse()//
				.description("200")//
				.content(new Content()//
						.addMediaType("application/json", new MediaType()//
								.schema(new Schema<>()//
										.$ref("#/components/schemas/" + outGoingMessageType.getName()))));
		op.responses(new ApiResponses()//
				.addApiResponse("200", response));
		item.post(op);
	}

	private void addRequest(Operation op, Class<?> klass) {
		RequestBody request = new RequestBody()//
				.required(true)//
				.content(new Content()//
						.addMediaType("application/json", new MediaType()//
								.schema(new Schema<>()//
										.$ref("#/components/schemas/" + klass.getName()))));
		op.requestBody(request);
	}

	private static String capitalize(String str) {
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	private static String prettyEndpoint(String s) {
		return Arrays.stream(s.split(" "))//
				.peek(BPMNFacade::capitalize)//
				.collect(Collectors.joining());
	}

	private static ObjectMapper getObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();

		mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}

	public Collection<File> writeOpenApi(File outputDir) {
		Collection<File> res = new HashSet<File>();
		for (Map.Entry<TParticipant, OpenAPI> entry : this.getOpenApiMap().entrySet()) {

			TParticipant participant = entry.getKey();
			OpenAPI oai = entry.getValue();

			File destination = Paths.get(outputDir.toString(), participant.getName().replace(' ', '_') + ".yaml")
					.toFile();
			try (FileWriter writer = new FileWriter(destination)) {
				writer.write(toYaml(oai));
				res.add(destination);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return res;

	}

	private static String toYaml(OpenAPI api) {
		try {
			ObjectMapper mapper = getObjectMapper();
			String oaiString = mapper.writer(new DefaultPrettyPrinter()).writeValueAsString(api);
			JsonNode jsonNodeTree = mapper.readTree(oaiString);

			return new YAMLMapper().writeValueAsString(jsonNodeTree);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	public void writeOpenAPIArtefacts(File outputDirectory) {
		createOutputDirIfNotThere(outputDirectory);
		Map<TParticipant, OpenAPI> map = this.getOpenApiMap();

		for (Map.Entry<TParticipant, OpenAPI> entry : map.entrySet()) {

			String participantNameEscaped = entry.getKey().getName().replace(' ', '_');
			OpenAPI api = entry.getValue();

			writeJavaServerStubs(participantNameEscaped, api, outputDirectory);
		}
	}

	private void writeJavaServerStubs(String participantNameEscaped, OpenAPI api, File outputDirectory) {
		ClientOptInput opts = new ClientOptInput();
		AbstractJavaCodegen config = new JavaJerseyServerCodegen();
		config.setOutputDir(
				Paths.get(outputDirectory.getPath(), participantNameEscaped, "generated_server_stub").toString());
		opts.config(config);
		opts.openAPI(api);

		new DefaultGenerator().opts(opts).generate();
	}

	private void createOutputDirIfNotThere(File outputDirectory) {
		try {
			Path outputDir = Paths.get(outputDirectory.toString());
			if (!Files.exists(outputDir)) {
				Files.createDirectories(outputDir);
			}
		} catch (IOException e) {
			LOGGER.error("failed to create directory : {}", outputDirectory.toString());
			System.exit(-1);
		}
	}
}