package com.github.ruediste.polymorphicGson;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks base classes for polymorphic serialization
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface GsonPolymorph {

}
