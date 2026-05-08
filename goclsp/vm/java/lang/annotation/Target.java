/*
 * @(#) $(JCGO)/goclsp/vm/java/lang/annotation/Target.java --
 * Java 5 @Target meta-annotation. See Documented.java for context.
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
public @interface Target
{
 ElementType[] value();
}
