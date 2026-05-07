/*
 * @(#) $(JCGO)/goclsp/vm/java/lang/Deprecated.java --
 * Built-in @Deprecated annotation type. classpath-0.93 omits this
 * (and the other JLS-5 built-in annotations); JCGO supplies it so
 * runtime reflection can resolve `Class.forName("java.lang.Deprecated")`
 * for proxy construction.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package java.lang;

import java.lang.annotation.Annotation;

public interface Deprecated extends Annotation
{
}
