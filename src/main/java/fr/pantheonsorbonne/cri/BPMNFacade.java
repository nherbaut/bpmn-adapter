package fr.pantheonsorbonne.cri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

public class BPMNFacade {

	private static final String BPMN2_DEFINITIONS = "bpmn2:definitions";
	private static final String XSD_ELEMENT = "xsd:element";
	private static final String XSD_SEQUENCE = "xsd:sequence";
	private static final String XSD_COMPLEX_TYPE = "xsd:complexType";
	private final TDefinitions definitions;
	private final Map<String, String> ns = new HashMap<String, String>();
	private final Map<String, Collection<Class<?>>> types = new HashMap<>();

	class OpenAPIDeserializer2 extends OpenAPIDeserializer {
		public Schema getSchema(ObjectNode node, String location) {
			ParseResult pr = new ParseResult();

			Schema res = getSchema(node, location, pr);
			if (!pr.isValid()) {
				throw new RuntimeException(pr.getMessages().stream().collect(Collectors.joining()));
			}
			return res;

		}

	}

	class JsonTypeAndFormatHelper {
		public String type;
		public String format;

		public JsonTypeAndFormatHelper(String type, String format) {
			this.type = type;
			this.format = format;
		}

		public JsonTypeAndFormatHelper(String type) {
			this.type = type;
			this.format = null;
		}

		public Schema toSchema() {
			if (Strings.isNullOrEmpty(format)) {
				return new Schema().type(type);
			} else if (format.startsWith("tns:")) {
				return new Schema().$ref("#/components/schemas/" + format.split("tns:")[1]);
			} else if (format.startsWith("xsd:")) {
				return new Schema().type(type).format(format.split("xsd:")[1]);
			} else {
				return new Schema().type(type).format(format);
			}
		}
	}

	final Map<String, JsonTypeAndFormatHelper> basicTypesMapping = Map.ofEntries(
			Map.entry("xsd:anySimpleType", new JsonTypeAndFormatHelper("string")),
			Map.entry("xsd:string", new JsonTypeAndFormatHelper("string")),
			Map.entry("xsd:boolean", new JsonTypeAndFormatHelper("boolean")),
			Map.entry("xsd:float", new JsonTypeAndFormatHelper("number", "float")),
			Map.entry("xsd:dateTime", new JsonTypeAndFormatHelper("string")),
			Map.entry("xsd:double", new JsonTypeAndFormatHelper("number", "double")),
			Map.entry("xsd:integer", new JsonTypeAndFormatHelper("number", "integer"))

	);
	private File bpmnFile;

	private InputStream getExampleChoreographModel() {

		return App.class.getClassLoader().getResourceAsStream("choreography.xml");
	}

	private static File[] getResourceFolderFiles(String folder) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		URL url = loader.getResource(folder);
		String path = url.getPath();
		return new File(path).listFiles();
	}

	public Components getComponents() {
		Components components = new Components();
		ObjectMapper mapper = new ObjectMapper();
		JsonSchemaGenerator generator = new JsonSchemaGenerator(mapper);

		try {

			for (Map.Entry<String, Collection<Class<?>>> type : this.types.entrySet()) {
				for (Class<?> klass : type.getValue()) {

					//create a json schema from this class
					JsonSchema jsonSchema = generator.generateSchema(klass);
					String jsonSchemaStr = mapper.writer().writeValueAsString(jsonSchema);
					
					//parse the schema as json
					ObjectNode objectNode = new ObjectMapper().readValue(jsonSchemaStr, ObjectNode.class);
					
					//use the unsealed Deserializer to generate the schema
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

			StreamSource ss = new StreamSource(getExampleChoreographModel());
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

			saxParser.parse(getExampleChoreographModel(), handler);

			return o.getValue();
		} catch (JAXBException | ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public TChoreography getChoreography() {
		return getDefinitionElements(TChoreography.class).findFirst().get();

	}

	private static String readResourceFromClassPath(String path) {
		try {
			return CharStreams.toString(new InputStreamReader(App.class.getClassLoader().getResourceAsStream(path)));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public BPMNFacade(File bpmnFile) {

		this.bpmnFile = bpmnFile;
		this.definitions = readBPMNFromEmbdedModel(bpmnFile);

		this.definitions.getImport().stream().filter(i -> i.getLocation().equals(i.getLocation()))
				.forEach(i -> types.put(i.getNamespace(),
						getExternalTypes(Paths.get(bpmnFile.getParentFile().getPath(), i.getLocation()).toFile())));

	}

	public List<TChoreographyTask> getChoreographyTasks() {
		return this.getChoreography().getFlowElement().stream().map(e -> e.getValue())
				.filter(obj -> obj instanceof TChoreographyTask).map(obj -> (TChoreographyTask) obj)
				.collect(Collectors.toList());
	}

	private <T extends TBaseElement> Stream<T> getDefinitionElements(Class<T> klass) {
		return definitions.getRootElement().stream()

				.map(e -> e.getValue())
				// .peek(System.out::println)
				.filter(e -> klass.isInstance(e)).map(e -> klass.cast(e));
	}

	public TMessage getMessage(QName qname) {
		return this.getDefinitionElements(TMessage.class).filter(m -> qname.getLocalPart().equals("#" + m.getId()))
				.findFirst().orElseThrow(NoSuchElementException::new);
	}

	public TMessageFlow getMessageFlow(QName qname) {
		return this.getChoreography().getMessageFlow().stream()
				.filter(m -> qname.getLocalPart().equals("#" + m.getId())).findFirst()
				.orElseThrow(() -> new NoSuchElementException(qname.toString()));
	}

	public TParticipant getParticipant(QName qname) {
		return this.getChoreography().getParticipant().stream()
				.filter(m -> qname.getLocalPart().equals("#" + m.getId())).findFirst()
				.orElseThrow(NoSuchElementException::new);
	}

	public TItemDefinition getItemDef(QName qname) {
		return this.getDefinitionElements(TItemDefinition.class)
				.filter(m -> qname.getLocalPart().equals("#" + m.getId())).findFirst()
				.orElseThrow(NoSuchElementException::new);
	}

	private void writeToStandardOutputWithModuleJsonSchema(final String fullyQualifiedClassName) {

	}

	private static final String GENERATED_PACKAGE = "";

	private Collection<Class<?>> getExternalTypes(File path) {

		Collection<Class<?>> res = new ArrayList<Class<?>>();
		URI schemaURI = URI.createFileURI(path.getPath());
		XSDEcoreBuilder xsdEcoreBuilder = new XSDEcoreBuilder();

		Collection<EObject> eCorePackages = xsdEcoreBuilder.generate(schemaURI);
		Deque<EClassifier> xsdKlasses = new ArrayDeque<EClassifier>(eCorePackages.stream().findFirst().get().eContents()
				.stream().map(o -> (EClassifier) o).collect(Collectors.toList()));

		ByteBuddy bb = new ByteBuddy();
		typeBuilder: while (!xsdKlasses.isEmpty()) {

			System.out.println("### " + xsdKlasses.size() + " to GO!");

			EClassifier klassifier = xsdKlasses.poll();
			if (klassifier.getName().equals("DocumentRoot")) {
				continue;
			}
			System.out.println("trying to generate dynamic type for " + klassifier.getName());
			DynamicType.Builder<?> typeBuilder = null;

			if (klassifier instanceof EClass) {
				typeBuilder = bb.subclass(Object.class);
				EClass klass = (EClass) klassifier;
				for (int i = 0; i < klass.getFeatureCount(); i++) {
					ETypedElement feature = klass.getEStructuralFeature(i);
					if(feature.getName().equals("group")) {
						continue;
					}
					Class<?> attributeClass = feature.getEType().getInstanceClass();

					if (attributeClass == null) {
						// can be a complex type, not sure if defined...
						try {
							attributeClass = Class.forName(GENERATED_PACKAGE + feature.getEType().getName());
						} catch (ClassNotFoundException e) {
							attributeClass = null;
						}

						if (attributeClass == null) {
							// type not already defined in the classloader
							System.out.println("missing type" + feature.getEType().getName()
									+ ", deferring complex type creation");
							xsdKlasses.addLast(klassifier);
							continue typeBuilder;
						}
					}
					String attributeName = feature.getName();
					if (feature.getUpperBound() != 1) {
						// List
						Generic attributeList = TypeDescription.Generic.Builder
								.parameterizedType(List.class, attributeClass).build();
						typeBuilder = typeBuilder.defineField(attributeName, attributeList,
								net.bytebuddy.description.modifier.Visibility.PUBLIC);
					} else {
						// scalar
						typeBuilder = typeBuilder.defineField(attributeName, attributeClass,
								net.bytebuddy.description.modifier.Visibility.PUBLIC);
					}

				}
			} else if (klassifier instanceof EEnum) {
				EEnum eenum = (EEnum) klassifier;
				typeBuilder = bb.makeEnumeration(
						eenum.getELiterals().stream().map(e -> e.getName()).collect(Collectors.toSet()));
			} else {
				System.out.println("don't know what to do with this" + klassifier + " [DROPING]");
				continue typeBuilder;
			}

			typeBuilder = typeBuilder.name(GENERATED_PACKAGE + klassifier.getName());
			Class<?> xsdType = typeBuilder.make()
					.load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION).getLoaded();
			res.add(xsdType);

		}

		return res;
	}

	public Class<?> getTypeFromMessage(TMessage message) {

		TItemDefinition itemDef = this.getItemDef(message.getItemRef());
		String xsdName = itemDef.getStructureRef().getLocalPart();

		Collection<Class<?>> klasses = this.types
				.get(this.getItemDef(message.getItemRef()).getStructureRef().getNamespaceURI());
		return klasses.stream()
				// .peek(System.out::println)
				// comparison is done ignore the case, since dynamic classes are capitalized
				.filter(k -> k.getName().equalsIgnoreCase(GENERATED_PACKAGE + xsdName)).findAny()
				.orElseThrow(() -> new NoSuchElementException(xsdName));
	}

}