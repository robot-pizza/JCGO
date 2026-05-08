/*
 * @(#) $(JCGO)/goclsp/vm/java/lang/annotation/Documented.java --
 * Java 5 @Documented marker. classpath-0.93 ships only the Annotation
 * interface from java.lang.annotation; this file (and its siblings
 * Retention, Target, Inherited, RetentionPolicy, ElementType) are
 * supplied by JCGO so user code can reference them at runtime.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package java.lang.annotation;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Documented
{
}
