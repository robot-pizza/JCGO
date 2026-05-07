/*
 * @(#) $(JCGO)/goclsp/vm/java/lang/annotation/Repeatable.java --
 * Java 8 @Repeatable marker annotation. classpath-0.93 predates
 * Java 8, so the type is supplied by JCGO.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package java.lang.annotation;

/**
 * Indicates that the annotation type whose declaration this
 * annotation decorates is repeatable. The value gives the
 * containing annotation type — its {@code value()} method must
 * return an array of the repeatable annotation's type.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Repeatable
{
 Class value();
}
