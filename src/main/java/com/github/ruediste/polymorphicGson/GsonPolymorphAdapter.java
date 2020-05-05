package com.github.ruediste.polymorphicGson;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.internal.bind.JsonTreeReader;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;

/**
 * Add support for polymorphic serialization to Gson
 */
public class GsonPolymorphAdapter implements TypeAdapterFactory {

	/**
	 * Style of representing type information in the serialized json
	 */
	public enum PolymorphStyle {
		/**
		 * the object is wrapped in another object, using a property name representing
		 * the type
		 */
		PROPERTY,
		/**
		 * The object is wrapped in an array. The first element represents the type, the
		 * second element is the object itself.
		 */
		ARRAY,
		/**
		 * a property named {@link GsonPolymorphAdapter#typePropertyName} is added to
		 * the object, representing the type
		 */
		TYPE_PROPERTY
	}

	/**
	 * Property name in the serialized json to represent type information in
	 * {@link PolymorphStyle#TYPE_PROPERTY} style
	 */
	public String typePropertyName = "@type";

	/**
	 * Extractor for the default type name if no @{@link GsonPolymorphName}
	 * annotation is present on a type
	 */
	public Function<Class<?>, String> defaultNameExtractor = cls -> {
		String name = cls.getSimpleName();
		if (name.isEmpty())
			throw new RuntimeException("Simple name of " + cls + " is empty");
		if (Character.isUpperCase(name.charAt((0))))
			return Character.toLowerCase(name.charAt(0)) + name.substring(1);
		return name;
	};

	private Map<Class<?>, HashMap<String, Class<?>>> classesByName = new HashMap<>();
	private PolymorphStyle style;

	/**
	 * Instantiate the adapter
	 * 
	 * @param style type representation style to be used
	 * @param cl    class loader to load classes during deserialization
	 * @param pkg   package to be scanned
	 */
	public GsonPolymorphAdapter(PolymorphStyle style, ClassLoader cl, String pkg) {
		this.style = style;

		// scan the classpath
		try (var scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().whitelistPackages(pkg).scan()) {

			// iterate over classes annotated with @GsonPolymorph
			for (ClassInfo baseClassInfo : scanResult.getClassesWithAnnotation(GsonPolymorph.class.getName())) {
				// build a map of all names of subclasses to the subclass
				var nameMap = new HashMap<String, Class<?>>();

				// add the names of the base class as well to simplify the deserializer
				var baseClass = cl.loadClass(baseClassInfo.getName());
				getNames(baseClass).forEach(name -> nameMap.put(name, baseClass));

				// iterate over subclasses
				for (var subClassInfo : baseClassInfo.getSubclasses()) {
					var subClass = cl.loadClass(subClassInfo.getName());
					for (var name : getNames(subClass)) {
						var existingClass = nameMap.put(name, subClass);
						if (existingClass != null) {
							throw new RuntimeException("Subclasses " + subClass + " and " + existingClass + " of "
									+ baseClassInfo + " map to the same name " + name);
						}
					}
				}
				classesByName.put(baseClass, nameMap);
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * get the name used for serialization
	 */
	private String getName(Class<?> cls) {
		var annotation = cls.getAnnotation(GsonPolymorphName.class);
		if (annotation != null)
			return annotation.value();

		return defaultNameExtractor.apply(cls);
	}

	/**
	 * get all names used for deserialization of a class
	 */
	private Set<String> getNames(Class<?> cls) {
		var result = new HashSet<String>();
		result.add(getName(cls));
		var altName = cls.getAnnotation(GsonPolymorphAltName.class);
		if (altName != null)
			result.addAll(Arrays.asList(altName.value()));
		return result;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {

		if (type.getRawType().isAnnotationPresent(GsonPolymorph.class)) {
			var classMap = classesByName.get(type.getRawType());
			if (classMap == null)
				throw new RuntimeException("Base class " + type.getRawType() + " was not scanned");

			// collect TypeAdapters for all subclasses
			Map<Class<?>, TypeAdapter> adapterByClass = new HashMap<>();
			Map<String, TypeAdapter> adapterByName = new HashMap<>();
			classMap.forEach((name, cls) -> {
				TypeAdapter t;
				if (cls == type.getRawType())
					t = gson.getDelegateAdapter(this, type);
				else
					t = gson.getAdapter(cls);
				adapterByClass.put(cls, t);
				adapterByName.put(name, t);
			});

			return new TypeAdapter<T>() {

				@Override
				public void write(JsonWriter out, T value) throws IOException {
					if (value == null) {
						out.nullValue();
						return;
					}

					String name = getName(value.getClass());
					switch (style) {
					case ARRAY:
						out.beginArray();
						out.value(name);
						adapterByClass.get(value.getClass()).write(out, value);
						out.endArray();
						break;
					case PROPERTY: {
						out.beginObject();

						out.name(name);
						adapterByClass.get(value.getClass()).write(out, value);
						out.endObject();
					}
						break;
					case TYPE_PROPERTY:
						JsonElement tree = adapterByClass.get(value.getClass()).toJsonTree(value);
						tree.getAsJsonObject().addProperty(typePropertyName, name);
						Streams.write(tree, out);
						break;
					default:
						throw new IllegalArgumentException();
					}

				}

				@Override
				public T read(JsonReader in) throws IOException {
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
						return null;
					}

					switch (style) {
					case ARRAY: {
						in.beginArray();
						String name = in.nextString();
						var result = getAdapter(adapterByName, name, type).read(in);
						in.endArray();
						return (T) result;
					}
					case PROPERTY: {
						in.beginObject();
						String name = in.nextName();
						var result = getAdapter(adapterByName, name, type).read(in);
						in.endObject();
						return (T) result;
					}
					case TYPE_PROPERTY:
						JsonObject tree = Streams.parse(in).getAsJsonObject();
						String name = tree.get(typePropertyName).getAsString();
						tree.remove(typePropertyName);
						return (T) getAdapter(adapterByName, name, type).read(new JsonTreeReader(tree));
					default:
						throw new IllegalArgumentException();
					}

				}

				private TypeAdapter getAdapter(Map<String, TypeAdapter> adapterByName, String name, TypeToken<T> type) {
					TypeAdapter result = adapterByName.get(name);
					if (result == null) {
						throw new RuntimeException("Unknown sub type " + name + " of type " + type);
					}
					return result;
				}
			};
		}
		return null;
	}
}
