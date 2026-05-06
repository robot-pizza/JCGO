/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/RecordSynthesis.java --
 * a part of JCGO translator.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package com.ivmaisoft.jcgo;

/**
 * Builds the synthetic class body for a record declaration. Slice 11
 * (Java 16, JEP 395) MVP: synthesizes private final fields, a canonical
 * constructor that assigns them, and one-arg accessor methods. Skips
 * equals/hashCode/toString synthesis and additional record-body members.
 */
final class RecordSynthesis {

    /**
     * Map from simple record class name → array of [componentName, componentType]
     * pairs (length = 2 * componentCount). Populated by buildBody so the
     * record-pattern lifter (slice 16) can decode positional sub-patterns into
     * accessor calls. Simple-name keying is fine for fixtures; cross-package
     * collisions would need fully-qualified keys later.
     */
    static final ObjHashtable componentsByName = new ObjHashtable();

    private RecordSynthesis() {
    }

    /**
     * Returns a synthesized class body Term equivalent to the record header
     * (private final fields + canonical ctor + accessors). Caller is
     * responsible for wrapping the result in a Seq with Empty.term per
     * ClassBody convention and constructing the outer ClassDeclaration.
     */
    static Term buildBody(String recordName, Term headerParams) {
        return buildBody(recordName, headerParams, Empty.newTerm());
    }

    /**
     * Slice 29: variant that folds user-supplied record-body members
     * into the synthesized output. A user-declared canonical ctor
     * (ConstrDeclaration whose arity matches the header) replaces the
     * synthesized default — JCGO doesn't auto-prepend the field
     * assignments yet, so the user is responsible for spelling
     * `this.x = x` style. Other members (extra methods, fields, static
     * initializers) pass through verbatim.
     */
    static Term buildBody(String recordName, Term headerParams,
            Term userBody) {
        ObjVector params = new ObjVector();
        flattenFormalParams(headerParams, params);

        ObjVector members = new ObjVector();

        Object[] componentInfo = new Object[params.size() * 2];
        for (int i = 0; i < params.size(); i++) {
            FormalParameter fp = (FormalParameter) params.elementAt(i);
            Term fieldType = fp.terms[1];
            String fieldName = paramName(fp);
            componentInfo[i * 2] = fieldName;
            componentInfo[i * 2 + 1] = fieldType;
            members.addElement(buildField(fieldType, fieldName));
            members.addElement(buildAccessor(fieldType, fieldName));
        }
        componentsByName.put(recordName, componentInfo);

        boolean userHasCanonicalCtor = userBody != null
                && userBody.notEmpty()
                && containsCanonicalCtor(userBody, recordName, params.size());
        if (!userHasCanonicalCtor) {
            members.addElement(buildCanonicalCtor(recordName, headerParams,
                    params));
        }
        if (userBody != null && userBody.notEmpty()) {
            members.addElement(userBody);
        }

        return new Seq(seqOf(members), Empty.newTerm());
    }

    private static boolean containsCanonicalCtor(Term node, String name,
            int arity) {
        if (!node.notEmpty()) return false;
        if (node instanceof Seq) {
            Seq s = (Seq) node;
            return containsCanonicalCtor(s.terms[0], name, arity)
                    || containsCanonicalCtor(s.terms[1], name, arity);
        }
        if (node instanceof TypeDeclaration) {
            Term inner = ((TypeDeclaration) node).getDeclTerm();
            if (inner instanceof ConstrDeclaration) {
                ConstrDeclaration ctor = (ConstrDeclaration) inner;
                if (name.equals(ctor.terms[0].dottedName())
                        && countParams(ctor.terms[1]) == arity) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int countParams(Term paramList) {
        if (!paramList.notEmpty()) return 0;
        if (paramList instanceof FormalParamList) {
            FormalParamList fpl = (FormalParamList) paramList;
            return countParams(fpl.terms[0]) + countParams(fpl.terms[1]);
        }
        return 1;
    }

    private static Term buildField(Term fieldType, String fieldName) {
        Term modifiers = new Seq(new AccModifier(AccModifier.PRIVATE),
                new AccModifier(AccModifier.FINAL));
        Term varDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, fieldName)),
                Empty.newTerm(), Empty.newTerm());
        Term field = new FieldDeclaration(fieldType, Empty.newTerm(),
                varDeclr);
        return new TypeDeclaration(modifiers, field);
    }

    private static Term buildAccessor(Term fieldType, String fieldName) {
        Term modifiers = new AccModifier(AccModifier.PUBLIC);
        Term body = new Block(new ReturnStatement(
                new QualifiedName(new LexTerm(LexTerm.ID, fieldName),
                        Empty.newTerm())));
        Term method = new MethodDeclaration(fieldType, Empty.newTerm(),
                new LexTerm(LexTerm.ID, fieldName),
                Empty.newTerm(), Empty.newTerm(), Empty.newTerm(), body);
        return new TypeDeclaration(modifiers, method);
    }

    private static Term buildCanonicalCtor(String recordName,
            Term headerParams, ObjVector params) {
        ObjVector stmts = new ObjVector();
        for (int i = 0; i < params.size(); i++) {
            FormalParameter fp = (FormalParameter) params.elementAt(i);
            String fieldName = paramName(fp);
            Term thisFieldAccess = new PrimaryFieldAccess(new This(),
                    new QualifiedName(new LexTerm(LexTerm.ID, fieldName),
                            Empty.newTerm()));
            Term assign = new Assignment(thisFieldAccess,
                    new LexTerm(LexTerm.EQUALS, "="),
                    new QualifiedName(new LexTerm(LexTerm.ID, fieldName),
                            Empty.newTerm()));
            stmts.addElement(new ExprStatement(assign));
        }
        Term body = stmts.size() > 0 ? seqOf(stmts) : Empty.newTerm();
        Term ctor = new ConstrDeclaration(
                new LexTerm(LexTerm.ID, recordName),
                headerParams, Empty.newTerm(), body);
        Term modifiers = new AccModifier(AccModifier.PUBLIC);
        return new TypeDeclaration(modifiers, ctor);
    }

    private static void flattenFormalParams(Term t, ObjVector out) {
        if (!t.notEmpty()) {
            return;
        }
        if (t instanceof FormalParamList) {
            FormalParamList fpl = (FormalParamList) t;
            flattenFormalParams(fpl.terms[0], out);
            flattenFormalParams(fpl.terms[1], out);
        } else if (t instanceof FormalParameter) {
            out.addElement(t);
        }
    }

    private static String paramName(FormalParameter fp) {
        return fp.terms[3].dottedName();
    }

    private static Term seqOf(ObjVector items) {
        if (items.size() == 0) {
            return Empty.newTerm();
        }
        Term result = (Term) items.elementAt(items.size() - 1);
        for (int i = items.size() - 2; i >= 0; i--) {
            result = new Seq((Term) items.elementAt(i), result);
        }
        return result;
    }
}
