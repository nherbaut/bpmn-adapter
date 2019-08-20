package fr.pantheonsorbonne.cri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;

class BPMNFacade {

	private static final String BPMN2_DEFINITIONS = "bpmn2:definitions";
	private static final String XSD_ELEMENT = "xsd:element";
	private static final String XSD_SEQUENCE = "xsd:sequence";
	private static final String XSD_COMPLEX_TYPE = "xsd:complexType";
	private final TDefinitions definitions;
	private final Map<String, String> ns = new HashMap<String, String>();
	private final Map<String, String> types = new HashMap<String, String>();

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

	private InputStream getExampleChoreographModel() {

		return App.class.getClassLoader().getResourceAsStream("choreography.xml");
	}

	private static File[] getResourceFolderFiles(String folder) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		URL url = loader.getResource(folder);
		String path = url.getPath();
		return new File(path).listFiles();
	}

	public Components getComonents(String resourcePath) {
		try {
			Components components = new Components();

			try (InputStream is = App.class.getClassLoader().getResourceAsStream(resourcePath)) {

				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(is);
				Element root = document.getDocumentElement();

				Map<String, String> nsMap = new HashMap<String, String>();
				String targetNamespace = "";
				for (int i = 0; i < root.getAttributes().getLength(); i++) {
					Node namespace = root.getAttributes().item(i);
					if (namespace.getNodeName().startsWith("xmlns:")) {
						nsMap.put(namespace.getNodeName().split(":")[1], namespace.getNodeValue());
					} else if (namespace.getNodeName().equals("targetNamespace")) {
						targetNamespace = namespace.getNodeValue();
					}

				}

				NodeList children = root.getChildNodes();
				for (int i = 0; i < children.getLength(); i++) {
					org.w3c.dom.Node node = children.item(i);

					if (XSD_COMPLEX_TYPE.equals(node.getNodeName())) {
						Schema<String> schema = new Schema<String>();
						schema.setType("object");

						String typeName = node.getAttributes().getNamedItem("name").getNodeValue();
						components.addSchemas(typeName, schema);

						System.out.println("handle " + typeName);
						for (int j = 0; j < node.getChildNodes().getLength(); j++) {
							Node sequence = node.getChildNodes().item(j);

							if (XSD_SEQUENCE.equals(sequence.getNodeName())) {
								System.out.println("\txsd:sequence");

								for (int k = 0; k < sequence.getChildNodes().getLength(); k++) {
									Node element = sequence.getChildNodes().item(k);
									if (XSD_ELEMENT.equals(element.getNodeName())) {
										String name = element.getAttributes().getNamedItem("name").getNodeValue();
										String type = element.getAttributes().getNamedItem("type").getNodeValue();

										JsonTypeAndFormatHelper jsonType = null;
										jsonType = new JsonTypeAndFormatHelper(name, type);

										schema.addProperties(name, jsonType.toSchema());
										schema.addRequiredItem(name);

										if (basicTypesMapping.containsKey(type)) {
											System.out.println(
													"\t\t" + name + " ( " + basicTypesMapping.get(type) + " ) ");
										}
									}
								}
							}
						}
					}
				}

				System.out.println(App.getObjectMapper().writer(new DefaultPrettyPrinter()).writeValueAsString(components));
				return components;
			}
		} catch (IOException | ParserConfigurationException | SAXException e) {
			e.printStackTrace();
			throw new RuntimeException(e);

		}
	}

	private TDefinitions readBPMNFromEmbdedModel(String modelName) {
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

	public BPMNFacade(String name) {

		writeToStandardOutputWithModuleJsonSchema(TChoreographyTask.class.getName());
		this.definitions = readBPMNFromEmbdedModel(name);

		this.definitions.getImport().stream().filter(i -> i.getLocation().equals(i.getLocation()))
				.forEach(i -> types.put(i.getNamespace(), readResourceFromClassPath(i.getLocation())));

	}

	public List<TChoreographyTask> getChoreographyTasks() {
		return this.getChoreography().getFlowElement().stream().map(e -> e.getValue())
				.filter(obj -> obj instanceof TChoreographyTask).map(obj -> (TChoreographyTask) obj)
				.collect(Collectors.toList());
	}

	private <T extends TBaseElement> Stream<T> getDefinitionElements(Class<T> klass) {
		return definitions.getRootElement().stream().map(e -> e.getValue()).filter(e -> klass.isInstance(e))

				.map(e -> klass.cast(e));
	}

	public TMessage getMessage(QName qname) {
		return this.getDefinitionElements(TMessage.class).filter(m -> qname.getLocalPart().equals("#" + m.getId()))
				.findFirst().orElseThrow(NoSuchElementException::new);
	}

	public TParticipant getParticipant(QName qname) {
		return this.getChoreography().getParticipant().stream()
				.filter(m -> qname.getLocalPart().equals("#" + m.getId())).findFirst()
				.orElseThrow(NoSuchElementException::new);
	}

	private void writeToStandardOutputWithModuleJsonSchema(final String fullyQualifiedClassName) {

	}

}