package fr.pantheonsorbonne.cri;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.junit.Test;
import org.xml.sax.SAXException;

import com.google.common.collect.Iterators;

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
