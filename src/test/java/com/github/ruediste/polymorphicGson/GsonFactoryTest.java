package com.github.ruediste.polymorphicGson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.github.ruediste.polymorphicGson.GsonPolymorphAdapter.PolymorphStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonFactoryTest {

	public static class Referencing {
		public Base ref;
		public SubClassA a;
	}

	@GsonPolymorph
	public static class Base {
		public int baseField;
	}

	public static class SubClassA extends Base {
		public int fieldA;

		public SubClassA() {
		}

		public SubClassA(int value) {
			fieldA = value;
		}
	}

	@GsonPolymorphAltName("b")
	public static class SubClassB extends Base {
		public int fieldB;

		public SubClassB() {
		}

		public SubClassB(int value) {
			fieldB = value;
		}
	}

	@ParameterizedTest
	@EnumSource()
	public void serialize(PolymorphStyle style) {
		var gson = buildGson(style);

		var referencing = new Referencing();
		referencing.ref = new SubClassB(5);
		referencing.a = new SubClassA(3);

		String json = gson.toJson(referencing);
		System.out.println(json);
		referencing = gson.fromJson(json, Referencing.class);
		assertEquals(5, ((SubClassB) referencing.ref).fieldB);
		assertEquals(3, referencing.a.fieldA);

		referencing.ref = new Base();
		referencing.ref.baseField = 4;
		json = gson.toJson(referencing);
		System.out.println(json);
		referencing = gson.fromJson(json, Referencing.class);
		assertEquals(4, referencing.ref.baseField);
		assertEquals(Base.class, referencing.ref.getClass());

		referencing.ref = null;
		referencing.a = null;
		json = gson.toJson(referencing);
		System.out.println(json);
		referencing = gson.fromJson(json, Referencing.class);
	}

	private Gson buildGson(PolymorphStyle style) {
		return new GsonBuilder().registerTypeAdapterFactory(
				new GsonPolymorphAdapter(style, getClass().getClassLoader(), "com.github.ruediste")).create();
	}

	@Test
	public void deserializeAltName() {
		var gson = buildGson(PolymorphStyle.TYPE_PROPERTY);
		String json = "{\"ref\":{\"fieldB\":5,\"baseField\":0,\"@type\":\"b\"}}";
		var referencing = gson.fromJson(json, Referencing.class);
		assertEquals(5, ((SubClassB) referencing.ref).fieldB);
	}
}
