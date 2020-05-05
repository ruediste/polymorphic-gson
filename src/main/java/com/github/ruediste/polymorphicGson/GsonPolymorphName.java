package com.github.ruediste.polymorphicGson;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Defines the name used for a class in the serialized json. If absent, by
 * default the {@link Class#getSimpleName() simple class name} us used,
 * converted to lower camel. Can be changed by replacing
 * {@link GsonPolymorphAdapter#defaultNameExtractor}
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface GsonPolymorphName {
	String value();
}
