/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/RecordSynthesis.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2026 Ivan Maidanski <ivmai@mail.ru>
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

    private RecordSynthesis() {
    }

    /**
     * Returns a synthesized class body Term equivalent to the record header
     * (private final fields + canonical ctor + accessors). Caller is
     * responsible for wrapping the result in a Seq with Empty.term per
     * ClassBody convention and constructing the outer ClassDeclaration.
     */
    static Term buildBody(String recordName, Term headerParams) {
        ObjVector params = new ObjVector();
        flattenFormalParams(headerParams, params);

        ObjVector members = new ObjVector();

        for (int i = 0; i < params.size(); i++) {
            FormalParameter fp = (FormalParameter) params.elementAt(i);
            Term fieldType = fp.terms[1];
            String fieldName = paramName(fp);
            members.addElement(buildField(fieldType, fieldName));
            members.addElement(buildAccessor(fieldType, fieldName));
        }
        members.addElement(buildCanonicalCtor(recordName, headerParams,
                params));

        return new Seq(seqOf(members), Empty.newTerm());
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
