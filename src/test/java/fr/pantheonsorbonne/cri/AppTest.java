package fr.pantheonsorbonne.cri;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.xsd.ecore.XSDEcoreBuilder;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.google.common.collect.Iterators;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

public class AppTest {

	private static final String GENERATED_PACKAGE = "generated.nicolas";

	private void dumpGeneratedClassNames() {
		try {
			Field f = ClassLoader.class.getDeclaredField("classes");
			f.setAccessible(true);

			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			Vector<Class> classes = (Vector<Class>) f.get(classLoader);
			Set<Class> klasses = new HashSet<Class>();
			Iterators.addAll(klasses, classes.iterator());
			klasses.stream().map(c -> c.getName()).filter(s -> s.startsWith(GENERATED_PACKAGE)).sorted()
					.forEach(System.out::println);
		} catch (Exception e) {
			/// falls through
		}
	}

	@Test
	public void shouldAnswerWithTrue() throws IOException, SAXException, InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException,
			NoSuchFieldException, ClassNotFoundException {

		BPMNFacade facade = new BPMNFacade("choreography.xml");

		URI schemaURI = URI.createFileURI("target/classes/types.xsd");
		XSDEcoreBuilder xsdEcoreBuilder = new XSDEcoreBuilder();

		Collection<EObject> eCorePackages = xsdEcoreBuilder.generate(schemaURI);
		Deque<EClassifier> xsdKlasses = new ArrayDeque<EClassifier>(eCorePackages.stream().findFirst().get().eContents()
				.stream().map(o -> (EClassifier) o).collect(Collectors.toList()));

		ByteBuddy bb = new ByteBuddy();
		typeBuilder: while (!xsdKlasses.isEmpty()) {
			dumpGeneratedClassNames();
			System.out.println("### " + xsdKlasses.size() + " to GO!");

			EClassifier klassifier = xsdKlasses.poll();

			System.out.println("trying to generate dynamic type for " + klassifier.getName());
			DynamicType.Builder<?> typeBuilder = null;

			if (klassifier instanceof EClass) {
				typeBuilder = bb.subclass(Object.class);
				EClass klass = (EClass) klassifier;
				for (int i = 0; i < klass.getFeatureCount(); i++) {
					ETypedElement feature = klass.getEStructuralFeature(i);
					Class<?> attributeClass = feature.getEType().getInstanceClass();
					if (attributeClass == null) {
						// can be a complex type, not sure if defined...
						try {
							attributeClass = Class.forName(GENERATED_PACKAGE + "." + feature.getEType().getName());
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
					typeBuilder = typeBuilder.defineField(attributeName, attributeClass,
							net.bytebuddy.description.modifier.Visibility.PUBLIC);

				}
			} else if (klassifier instanceof EEnum) {
				EEnum eenum = (EEnum) klassifier;
				typeBuilder = bb.makeEnumeration(
						eenum.getELiterals().stream().map(e -> e.getName()).collect(Collectors.toSet()));
			} else {
				System.out.println("don't know what to do with this" + klassifier + " [DROPING]");
				continue typeBuilder;
			}

			typeBuilder=typeBuilder.name(GENERATED_PACKAGE + "." + klassifier.getName());
			Class<?> xsdType = typeBuilder.make()
					.load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION).getLoaded();
			System.out.println("Type " + xsdType.getName() + " loaded in the class Loader");
			ObjectMapper mapper = new ObjectMapper();
			JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
			JsonSchema schema = schemaGen.generateSchema(xsdType);
			System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema));

		}
	}

//		DynamicType.Unloaded<?> minionsTypeUnLoaded = bb.subclass(Object.class).name("Minion")
//				.defineField("name", String.class, Visibility.PUBLIC).make();
//		
//		Class<?> minionsType = minionsTypeUnLoaded.load(getClass().getClassLoader(),ClassLoadingStrategy.Default.INJECTION)
//				.getLoaded();
//
//		Generic minionCollection = TypeDescription.Generic.Builder.parameterizedType(List.class, minionsType).build();
//
//		DynamicType.Unloaded<?> bossTypeUnloaded = bb.subclass(Object.class).name("Boss").defineField("name", String.class, Visibility.PUBLIC)
//				.defineField("minions", minionCollection, Visibility.PUBLIC).make();
//				
//		Class<?> bossType = bossTypeUnloaded.load(getClass().getClassLoader(),ClassLoadingStrategy.Default.INJECTION)
//				.getLoaded();
//
//
//		ObjectMapper mapper = new ObjectMapper();
//		JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
//		JsonSchema schema = schemaGen.generateSchema(bossType);
//
//		System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema));

}
