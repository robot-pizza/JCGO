/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/Parser.java --
 * a part of JCGO translator.
 **
 * Originally generated from jcgo.atg by the (now-lost) jcoco115 fork
 * of Coco/R for Java. Since the regenerator is unavailable upstream,
 * this file is now hand-maintained.
 */

/*
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 *
 * Hand-edited to lift the JLS-1+2 source-language ceiling: foreach,
 * varargs, static imports, annotations, multi-catch, strings/arrows in
 * switch, var, default interface methods, pattern instanceof, records,
 * sealed types, try-with-resources, switch expressions with yield, and
 * pattern switch.
 *
 * Licensed under the same terms as JCGO upstream:
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package com.ivmaisoft.jcgo;

public class Parser {
	private static final int maxT = 104;

	private static final boolean T = true;
	private static final boolean x = false;
	private static final int minErrDist = 2;
	private static int errDist = minErrDist;

	static Token token;   // last recognized token
	static Token t;       // lookahead token

	// Slice 45: stack of currently-active generic type-parameter
	// names. Each element is an ObjVector of paired entries
	// [name, bound, name, bound, ...]. Pushed when entering a generic
	// class / interface / record / method / constructor body, popped
	// on exit. SimpleType uses this to substitute single-id type-param
	// references with their erasure (Object, or the bound).
	private static final ObjVector typeParamScopes = new ObjVector();

	// Slice 45b: side-channel from generic AST nodes (ClassDeclaration,
	// IfaceDeclaration, MethodDeclaration, ConstrDeclaration) to their
	// original `<T, U extends X>` declaration list. Same paired layout
	// as the scope vectors above. Codegen passes (slice 50 reflection,
	// slice 51 bridge methods) read this through getGenericSignature
	// instead of trying to recover the info from the erased AST.
	private static final ObjHashtable genericSignatures = new ObjHashtable();

	static ObjVector getGenericSignature(Term decl) {
		return decl == null ? null
				: (ObjVector) genericSignatures.get(decl);
	}

	private static void recordGenericSignature(Term decl, ObjVector entries) {
		if (decl != null && entries != null && entries.size() > 0) {
			genericSignatures.put(decl, entries);
		}
	}

	// Slice 52: side-channel from a sealed class/interface AST node to
	// its `permits` list (ObjVector<String> of dotted class names).
	// Read by ClassDeclaration.processPass0 to thread the list onto
	// ClassDefinition for runtime enforcement.
	private static final ObjHashtable permitsLists = new ObjHashtable();

	static ObjVector getPermitsList(Term decl) {
		return decl == null ? null : (ObjVector) permitsLists.get(decl);
	}

	private static void recordPermitsList(Term decl, ObjVector names) {
		if (decl != null && names != null && names.size() > 0) {
			permitsLists.put(decl, names);
		}
	}

	// Slice 49: pending list of declaration-annotation type names
	// captured during the most recent ModifierSeq / AccModifier parse.
	// Drained by the surrounding declaration (ClassDeclaration,
	// MemberDecl wrapping, top-level type decl) and attached to the
	// declaration's AST node via a side channel. Type-use annotation
	// captures are excluded by the inTypeUseAnnotationContext flag.
	private static ObjVector pendingAnnotationNames = new ObjVector();

	private static boolean inTypeUseAnnotationContext = false;

	private static final ObjHashtable annotationsByDecl = new ObjHashtable();

	static ObjVector getDeclarationAnnotations(Term decl) {
		return decl == null ? null
				: (ObjVector) annotationsByDecl.get(decl);
	}

	private static int snapshotPendingAnnotations() {
		return pendingAnnotationNames.size();
	}

	// Slice 86: the args list mirrors the names list 1:1; trim both
	// in lockstep when taking a slice so the parallel structure stays
	// aligned for downstream emission.
	private static ObjVector pendingAnnotationArgsSinceTake;

	private static ObjVector takePendingAnnotationsSince(int snap) {
		int cur = pendingAnnotationNames.size();
		if (cur <= snap) {
			pendingAnnotationArgsSinceTake = null;
			return null;
		}
		ObjVector taken = new ObjVector();
		ObjVector takenArgs = new ObjVector();
		for (int i = snap; i < cur; i++) {
			taken.addElement(pendingAnnotationNames.elementAt(i));
			if (i < pendingAnnotationArgs.size()) {
				takenArgs.addElement(pendingAnnotationArgs.elementAt(i));
			} else {
				takenArgs.addElement("");
			}
		}
		while (pendingAnnotationNames.size() > snap) {
			pendingAnnotationNames.removeElementAt(
					pendingAnnotationNames.size() - 1);
		}
		while (pendingAnnotationArgs.size() > snap) {
			pendingAnnotationArgs.removeElementAt(
					pendingAnnotationArgs.size() - 1);
		}
		pendingAnnotationArgsSinceTake = takenArgs;
		return taken;
	}

	private static void recordAnnotations(Term decl, ObjVector names) {
		if (decl != null && names != null && names.size() > 0) {
			annotationsByDecl.put(decl, names);
			// Slice 86: lockstep store of arg-text list captured in
			// the most recent takePendingAnnotationsSince call.
			ObjVector args = pendingAnnotationArgsSinceTake;
			pendingAnnotationArgsSinceTake = null;
			if (args != null && args.size() == names.size()) {
				annotationArgsByDecl.put(decl, args);
			}
		}
	}

	// Slice 86: parallel to annotationsByDecl; stores the arg-text
	// ObjVector for each declaration. Read by codegen so each
	// emitted annotation type-name has a matching arg-text slot.
	private static final ObjHashtable annotationArgsByDecl =
			new ObjHashtable();

	static ObjVector getDeclarationAnnotationArgs(Term decl) {
		return decl == null ? null
				: (ObjVector) annotationArgsByDecl.get(decl);
	}

	// Slice 49 ext (parameter annotations): side channel from a
	// FormalParameter AST node to the annotation type names that
	// preceded it (e.g. `void foo(@NonNull String x)` records
	// ["NonNull"] on the FormalParameter for x). Read by
	// MethodDeclaration.processPass1 (and ConstrDeclaration) which
	// builds a per-param ObjVector and threads it onto MethodDefinition.
	private static final ObjHashtable paramAnnotationsByDecl =
			new ObjHashtable();

	// TODO #3: parallel to paramAnnotationsByDecl; stores per-param
	// annotation arg-text strings (paren contents) so runtime
	// Method.getParameterAnnotations can build full proxies, not just
	// markers.
	private static final ObjHashtable paramAnnotationArgsByDecl =
			new ObjHashtable();

	static ObjVector getParamAnnotations(Term param) {
		return param == null ? null
				: (ObjVector) paramAnnotationsByDecl.get(param);
	}

	static ObjVector getParamAnnotationArgs(Term param) {
		return param == null ? null
				: (ObjVector) paramAnnotationArgsByDecl.get(param);
	}

	private static void recordParamAnnotations(Term param, ObjVector names) {
		if (param != null && names != null && names.size() > 0) {
			paramAnnotationsByDecl.put(param, names);
			ObjVector args = pendingAnnotationArgsSinceTake;
			pendingAnnotationArgsSinceTake = null;
			if (args != null && args.size() == names.size()) {
				paramAnnotationArgsByDecl.put(param, args);
			}
		}
	}



	public static void Error(int n) {
		if (errDist >= minErrDist) Scanner.err.ParsErr(n, t.line, t.col);
		errDist = 0;
	}

	public static void SemError(String msg) {
		if (errDist >= minErrDist) Scanner.err.SemErr(msg, token.line, token.col);
		errDist = 0;
	}

	public static void SemError(int n) {
		if (errDist >= minErrDist) Scanner.err.SemErr(n, token.line, token.col);
		errDist = 0;
	}

	public static boolean Successful() {
		return Scanner.err.count == 0;
	}

	public static String LexString() {
		return token.str;
	}

	public static String LexName() {
		return token.val;
	}

	public static String LookAheadString() {
		return t.str;
	}

	public static String LookAheadName() {
		return t.val;
	}

	private static void Get() {
		for (;;) {
			token = t;
			if (peekedCount > 0) {
				t = peekedTokens[0];
				for (int i = 0; i < peekedCount - 1; i++) {
					peekedTokens[i] = peekedTokens[i + 1];
				}
				peekedTokens[--peekedCount] = null;
				peekedTokens[peekedCount] = null;
			} else {
				t = Scanner.Scan();
			}
			if (t.kind <= maxT) {errDist++; return;}

			t = token;
		}
	}

	// Multi-token lookahead extension (Path B, slice 1: foreach detection;
	// extended in slice 23b for parenthesized-lambda detection which needs
	// unbounded peek to scan past the matching `)`).
	// peek(1) returns t (the standard Coco/R lookahead). peek(n) for n>=2
	// reads ahead non-destructively from Scanner; the buffered tokens are
	// consumed by subsequent Get() calls so the parse state is preserved.
	private static Token[] peekedTokens = new Token[8];
	private static int peekedCount = 0;

	private static Token peek(int n) {
		if (n == 1) return t;
		while (peekedCount < n - 1) {
			if (peekedCount >= peekedTokens.length) {
				Token[] grown = new Token[peekedTokens.length * 2];
				for (int i = 0; i < peekedCount; i++) grown[i] = peekedTokens[i];
				peekedTokens = grown;
			}
			Token tk;
			do { tk = Scanner.Scan(); } while (tk.kind > maxT);
			peekedTokens[peekedCount++] = tk;
		}
		return peekedTokens[n - 2];
	}

	private static void Expect(int n) {
		if (t.kind == n) Get(); else Error(n);
	}

	private static boolean StartOf(int s) {
		return set[s][t.kind];
	}

	private static Term UnaryWithIdentTailOrDimExprs(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 13) {
			Get();
			z = NewInstOrSuperOrMethodInvoke(a);
		} else if (t.kind == 43) {
			Get();
			z = DimensionExpressionSeq(a);
		} else Error(105);
		return z;
	}

	private static Term ExprBracketOptNewArrayDims(Term b, Term c) {
		Term z;
		Term d, f = null;
		d = JavaExpression();
		Expect(44);
		if (t.kind == 43) {
			f = NewArrayBody(b, new DimsList(c, new DimExpr(d)));
		}
		z = f != null ? f : new ArrayCreation(b, new DimsList(c, new DimExpr(d)));
		
		return z;
	}

	private static Term BracketDimSpecs(Term b, Term c) {
		Term z;
		Term d = Empty.term;
		Expect(44);
		if (t.kind == 43) {
			d = DimSpecSeq();
		}
		z = new ArrayCreation(b, c, new DimSpec(d));
		return z;
	}

	private static Term NewArrayTail(Term b, Term c) {
		Term z;
		z = Empty.term;
		if (t.kind == 44) {
			z = BracketDimSpecs(b, c);
		} else if (StartOf(1)) {
			z = ExprBracketOptNewArrayDims(b, c);
		} else Error(106);
		return z;
	}

	private static Term NewArrayBody(Term b, Term c) {
		Term z;
		Expect(43);
		z = NewArrayTail(b, c);
		return z;
	}

	private static Term ExprBracketOptNewArrayBody(Term b) {
		Term z;
		Term d, f = null;
		d = JavaExpression();
		Expect(44);
		if (t.kind == 43) {
			f = NewArrayBody(b, new DimExpr(d));
		}
		z = f != null ? f : new ArrayCreation(b, new DimExpr(d));
		
		return z;
	}

	private static Term BracketDimsArrayInit(Term b) {
		Term z;
		Term c = Empty.term, d;
		if (t.kind == 43) {
			c = DimSpecSeq();
		}
		d = ArrayInitializer();
		z = new AnonymousArray(b, new DimSpec(c), d);
		return z;
	}

	private static Term NewArrayInstanceTail(Term b) {
		Term z;
		z = Empty.term;
		if (t.kind == 44) {
			Get();
			z = BracketDimsArrayInit(b);
			if (t.kind == 43) {
				Get();
				z = DimensionExpressionSeq(z);
			}
		} else if (StartOf(1)) {
			z = ExprBracketOptNewArrayBody(b);
		} else Error(107);
		return z;
	}

	private static Term ArgumentsOptClassBody(Term b) {
		Term z;
		Term d = Empty.term, f = Empty.term;

		if (canStartArg()) {
			d = ArgumentList();
		}
		Expect(12);
		if (t.kind == 28) {
			f = ClassBody();
		}
		z = new InstanceCreation(b, d, f);
		return z;
	}

	private static Term NewInstanceBody(Term b) {
		Term z;
		z = Empty.term;
		if (t.kind == 11) {
			Get();
			z = ArgumentsOptClassBody(b);
		} else if (t.kind == 43) {
			Get();
			z = NewArrayInstanceTail(b);
		} else Error(108);
		return z;
	}

	private static Term NewPrimArrayInstanceTail() {
		Term z;
		Term b;
		b = PrimitiveType();
		Expect(43);
		z = NewArrayInstanceTail(b);
		return z;
	}

	private static Term QualIdentNewInstanceTail() {
		Term z;
		Term b;
		b = QualifiedIdentifier();
		// Slice 24: `new Foo<...>(args)` — consume + erase the type args.
		// Also handles the diamond `<>` (zero-content angle pair).
		if (t.kind == 73) {
			consumeGenericArgs();
		}
		z = NewInstanceBody(new ClassOrIfaceType(b));
		return z;
	}

	private static Term IdentNewInstanceOrPrimArrTail() {
		Term z;
		z = Empty.term;
		if (t.kind == 1 || t.kind == 7) {
			z = QualIdentNewInstanceTail();
		} else if (StartOf(2)) {
			z = NewPrimArrayInstanceTail();
		} else Error(109);
		return z;
	}

	private static Term UnaryWithNewOrStrBody() {
		Term z;
		z = Empty.term;
		if (t.kind == 102) {
			Get();
			z = IdentNewInstanceOrPrimArrTail();
		} else if (t.kind == 34) {
			Get();
			Expect(13);
			Expect(23);
			z = new ClassLiteral(new PrimitiveType(Type.VOID));
			
		} else if (t.kind == 5) {
			Get();
			z = new StringLiteral(token.val);
		} else Error(110);
		return z;
	}

	private static Term UnaryWithPrimitiveTail(Term a) {
		Term z;
		Term d = null;
		Expect(13);
		Expect(23);
		if (t.kind == 13) {
			d = ThisOptMethodAccessTail(new ClassLiteral(a));
		}
		z = d != null ? d : new ClassLiteral(a);
		
		return z;
	}

	private static Term ExprBrackDimExprsUnaryIndents(Term a) {
		Term z;
		Term c, e = null, f = null, g = null;
		
		c = JavaExpression();
		Expect(44);
		if (t.kind == 43) {
			Get();
			e = DimensionExpressionSeq(new ArrayAccess(new Expression(a), c));
		}
		if (t.kind == 13) {
			f = UnaryWithIdentTailSeq(e != null ? e :
new ArrayAccess(new Expression(a), c));
		}
		if (t.kind == 96 || t.kind == 97) {
			g = IncDecOp();
		}
		z = g != null ? new PostfixOp(f != null ? f : e != null ? e :
		     new ArrayAccess(new Expression(a), c), g) : f != null ? f :
		     e != null ? e : new ArrayAccess(new Expression(a), c);
		
		return z;
	}

	private static Term BracketDimsOptUnaryPrim(Term a) {
		Term z;
		Term c = Empty.term, d = null;
		
		Expect(44);
		if (t.kind == 43) {
			c = DimSpecSeq();
		}
		if (t.kind == 13) {
			d = UnaryWithPrimitiveTail(new TypeWithDims(new ClassOrIfaceType(a),
new DimSpec(c)));
		}
		z = d != null ? d : new TypeWithDims(new ClassOrIfaceType(a),
		     new DimSpec(c));
		
		return z;
	}

	private static Term ClassOrThisOrNewInstCreation(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 23) {
			Get();
			z = new ClassLiteral(new ClassOrIfaceType(a));
		} else if (t.kind == 102) {
			Get();
			z = InnerNewInstanceCreation(new Expression(a));
		} else if (t.kind == 103) {
			Get();
			z = new This(new ClassOrIfaceType(a));
		} else Error(111);
		return z;
	}

	private static Term UnaryWithIdentQualified(Term a) {
		Term z;
		Term c, d = null;
		c = Identifier();
		if (StartOf(3)) {
			d = UnaryWithIdentBody(new QualifiedName(a, c));
		}
		z = d != null ? d : new Expression(new QualifiedName(a, c));
		
		return z;
	}

	private static Term UnaryWithIdentDotInstanceTail(Term a) {
		Term z;
		Term c, d = null;
		c = ClassOrThisOrNewInstCreation(a);
		if (t.kind == 13) {
			d = ThisOptMethodAccessTail(c);
		}
		z = d != null ? d : c;
		return z;
	}

	private static Term UnaryWithIdentBracketTail(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 44) {
			z = BracketDimsOptUnaryPrim(a);
		} else if (StartOf(1)) {
			z = ExprBrackDimExprsUnaryIndents(a);
		} else Error(112);
		return z;
	}

	private static Term UnaryWithIdentDotTail(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 101) {
			Get();
			z = SuperConstrMethodAccess(new Expression(a));
		} else if (t.kind == 23 || t.kind == 102 || t.kind == 103) {
			z = UnaryWithIdentDotInstanceTail(a);
		} else if (t.kind == 73) {
			// Slice 30: explicit type-witness invocation —
			// `Foo.<String>method(args)`. Consume + erase, then
			// continue as if the `.id` came directly.
			consumeGenericArgs();
			if (t.kind != 1 && t.kind != 7) {
				Error(113);
				return Empty.newTerm();
			}
			z = UnaryWithIdentQualified(a);
		} else if (t.kind == 1 || t.kind == 7) {
			z = UnaryWithIdentQualified(a);
		} else Error(113);
		return z;
	}

	private static Term UnaryWithIdentArgsBody(Term a) {
		Term z;
		Term c = Empty.term, e = null, f = null, g = null;

		if (canStartArg()) {
			c = ArgumentList();
		}
		Expect(12);
		if (t.kind == 43) {
			Get();
			e = DimensionExpressionSeq(new MethodInvocation(a, c));
		}
		if (t.kind == 13) {
			f = UnaryWithIdentTailSeq(e != null ? e : new MethodInvocation(a, c));
		}
		if (t.kind == 96 || t.kind == 97) {
			g = IncDecOp();
		}
		z = g != null ? new PostfixOp(f != null ? f : e != null ? e :
		     new MethodInvocation(a, c), g) : f != null ? f : e != null ? e :
		     new MethodInvocation(a, c);
		
		return z;
	}

	private static Term UnaryWithIdentBody(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 96) {
			Get();
			z = new PostfixOp(new Expression(a),
			     new LexTerm(LexTerm.INCREMENT, token.val));
			
		} else if (t.kind == 97) {
			Get();
			z = new PostfixOp(new Expression(a),
			     new LexTerm(LexTerm.DECREMENT, token.val));
			
		} else if (t.kind == 11) {
			Get();
			z = UnaryWithIdentArgsBody(a);
		} else if (t.kind == 13) {
			Get();
			z = UnaryWithIdentDotTail(a);
		} else if (t.kind == 43) {
			Get();
			z = UnaryWithIdentBracketTail(a);
		} else Error(114);
		return z;
	}

	private static Term ThisOptMethodAccessTail(Term a) {
		Term z;
		Term b, c = null;
		b = UnaryWithIdentTailSeq(a);
		if (t.kind == 96 || t.kind == 97) {
			c = IncDecOp();
		}
		z = c != null ? new PostfixOp(b, c) : b;
		return z;
	}

	private static Term ThisOptConstrMethodAccessTail() {
		Term z;
		z = Empty.term;
		if (t.kind == 11) {
			Get();
			z = ExplicitConstrInvoke(Empty.term, new This());
		} else if (t.kind == 13) {
			z = ThisOptMethodAccessTail(new This());
		} else Error(115);
		return z;
	}

	private static Term InnerSuperConstrInvocation(Term a) {
		Term z;
		Term e = Empty.term;
		Expect(11);
		if (canStartArg()) {
			e = ArgumentList();
		}
		Expect(12);
		z = new ConstructorCall(a, new Super(), e);
		return z;
	}

	private static Term InnerNewInstanceCreation(Term a) {
		Term z;
		Term d, f = Empty.term, h = Empty.term;

		d = Identifier();
		Expect(11);
		if (canStartArg()) {
			f = ArgumentList();
		}
		Expect(12);
		if (t.kind == 28) {
			h = ClassBody();
		}
		z = new InstanceCreation(a, d, f, h);
		return z;
	}

	private static Term NewInstOrSuperOrMethodInvoke(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 23) {
			Get();
			z = new ClassLiteral(new ClassOrIfaceType(a));
		} else if (t.kind == 102) {
			Get();
			z = InnerNewInstanceCreation(a);
		} else if (t.kind == 101) {
			Get();
			z = InnerSuperConstrInvocation(a);
		} else if (t.kind == 73) {
			// Slice 30: generic method type-witness — `Foo.<T>bar(args)`.
			// Consume + erase the type arguments, then continue as a
			// regular field/method invocation on the identifier that
			// follows.
			consumeGenericArgs();
			if (t.kind != 1 && t.kind != 7) {
				Error(116);
				return Empty.newTerm();
			}
			z = FieldMethodInvocation(a);
		} else if (t.kind == 1 || t.kind == 7) {
			z = FieldMethodInvocation(a);
		} else Error(116);
		return z;
	}

	private static Term DimensionExpressionSeq(Term a) {
		Term z;
		Term c, e = null;
		c = JavaExpression();
		Expect(44);
		if (t.kind == 43) {
			Get();
			e = DimensionExpressionSeq(new ArrayAccess(a, c));
		}
		z = e != null ? e : new ArrayAccess(a, c);
		
		return z;
	}

	private static Term PrimaryMethodInvoke(Term a, Term c) {
		Term z;
		Term e = Empty.term;
		Expect(11);
		if (canStartArg()) {
			e = ArgumentList();
		}
		Expect(12);
		z = new MethodInvocation(a, c, e);
		return z;
	}

	private static Term UnaryWithIdentTailSeq(Term a) {
		Term z;
		Term c, d = null;
		Expect(13);
		c = NewInstOrSuperOrMethodInvoke(a);
		if (t.kind == 13) {
			d = UnaryWithIdentTailSeq(c);
		}
		z = d != null ? d : c;
		return z;
	}

	private static Term FieldMethodInvocation(Term a) {
		Term z;
		Term c, d = null, e = null;
		c = Identifier();
		if (t.kind == 11) {
			d = PrimaryMethodInvoke(a, c);
		}
		if (t.kind == 43) {
			Get();
			e = DimensionExpressionSeq(d != null ?
d : new PrimaryFieldAccess(a, c));
		}
		z = e != null ? e : d != null ? d : new PrimaryFieldAccess(a, c);
		
		return z;
	}

	// Slice 36: gate for `if (StartOf(1)) ArgumentList()` call sites.
	// Adds `switch` (kind 53) to the regular expression-starter set so
	// `f(switch (x){...})` actually enters the arg parser.
	private static boolean canStartArg() {
		return StartOf(1) || t.kind == 53;
	}

	private static Term ArgumentList() {
		Term z;
		Term a, c = null;
		// Slice 33: accept lambda or method-reference as an argument.
		// MethodInvocation.processPass1 pre-resolves the receiver/method
		// to thread the formal parameter type into c.currentVarType
		// before pass1ing the lambda body.
		// Slice 36: also accept switch-expression args. The hoister at
		// the surrounding statement level pulls them out into a
		// preamble decl+switch-stmt and replaces the arg with a temp.
		if (t.kind == 53) {
			a = SwitchExpressionParse();
		} else if (looksLikeLambda()) {
			a = LambdaParse();
		} else if (looksLikeMethodRef()) {
			a = MethodRefParse();
		} else {
			a = JavaExpression();
		}
		if (t.kind == 27) {
			Get();
			c = ArgumentList();
		}
		z = c != null ? (Term) (new ParameterList(new Argument(a), c)) :
		     new Argument(a);

		return z;
	}

	private static Term SuperMethodAccessTail(Term a) {
		Term z;
		Term c, d = null, e = null;
		c = FieldMethodInvocation(a);
		if (t.kind == 13) {
			d = UnaryWithIdentTailSeq(c);
		}
		if (t.kind == 96 || t.kind == 97) {
			e = IncDecOp();
		}
		z = e != null ? new PostfixOp(d != null ? d : c, e) : d != null ? d : c;
		
		return z;
	}

	private static Term ExplicitConstrInvoke(Term a, Term c) {
		Term z;
		Term e = Empty.term;
		if (canStartArg()) {
			e = ArgumentList();
		}
		Expect(12);
		z = new ConstructorCall(a, c, e);
		return z;
	}

	private static Term UnaryWithParaComplexTail(Term a) {
		Term z;
		Term b, c = null, d = null;
		b = UnaryWithIdentTailOrDimExprs(a);
		if (t.kind == 13) {
			c = UnaryWithIdentTailSeq(b);
		}
		if (t.kind == 96 || t.kind == 97) {
			d = IncDecOp();
		}
		z = d != null ? new PostfixOp(c != null ? c : b, d) : c != null ? c : b;
		
		return z;
	}

	private static Term PostfixOptUnaryExpr(Term b) {
		Term z;
		Term d, e = null;
		d = IncDecOp();
		if (StartOf(4)) {
			e = UnaryExpressionTail();
		}
		z = e != null ? (Term) (new CastExpression(b, new PrefixOp(d, e))) :
		     new PostfixOp(new ParenExpression(b), d);
		
		return z;
	}

	private static Term CastPlusMinusUnary(Term b) {
		Term z;
		Term d, e; if (!b.isType()) return null;
		
		d = NegatePlusMinusOp();
		e = UnaryExpression();
		z = new CastExpression(b, new UnaryExpression(d, e));
		
		return z;
	}

	private static Term UnaryWithParaTail(Term b) {
		Term z;
		z = Empty.term; Term d;
		// Slice 34: lambda or method-reference body of a cast — the
		// cast type provides the lambda's target functional interface.
		// Slice 38: switch-expression body of a cast — the surrounding
		// statement-level hoister picks it up and lifts to a temp.
		if (looksLikeLambda()) {
			z = new CastExpression(b, LambdaParse());
		} else if (looksLikeMethodRef()) {
			z = new CastExpression(b, MethodRefParse());
		} else if (t.kind == 53) {
			z = new CastExpression(b, SwitchExpressionParse());
		} else if (StartOf(5)) {
			z = CastPlusMinusUnary(b);
		} else if (t.kind == 96 || t.kind == 97) {
			z = PostfixOptUnaryExpr(b);
		} else if (t.kind == 13 || t.kind == 43) {
			z = UnaryWithParaComplexTail(new ParenExpression(b));
		} else if (StartOf(4)) {
			d = UnaryExpressionTail();
			z = new CastExpression(b, d);
		} else Error(117);
		return z;
	}

	private static Term UnaryWithIdent() {
		Term z;
		Term a, b = null;
		a = Identifier();
		if (StartOf(3)) {
			b = UnaryWithIdentBody(new QualifiedName(a));
		}
		z = b != null ? b : new Expression(new QualifiedName(a));
		
		return z;
	}

	private static Term UnaryWithNewOrStr() {
		Term z;
		Term a, b = null;
		a = UnaryWithNewOrStrBody();
		if (t.kind == 13) {
			b = ThisOptMethodAccessTail(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term UnaryWithPrimitive() {
		Term z;
		Term a, b = null, c = null;
		a = PrimitiveType();
		if (t.kind == 43) {
			b = DimSpecSeq();
		}
		if (t.kind == 13) {
			c = UnaryWithPrimitiveTail(b != null ? new TypeWithDims(a, b) : a);
		}
		z = c != null ? c : b != null ? new TypeWithDims(a, b) : a;
		
		return z;
	}

	private static Term ThisOptConstrMethodAccess() {
		Term z;
		Term b = null;
		Expect(103);
		if (t.kind == 11 || t.kind == 13) {
			b = ThisOptConstrMethodAccessTail();
		}
		z = b != null ? b : new This();
		return z;
	}

	private static Term SuperConstrMethodAccess(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 11) {
			Get();
			z = ExplicitConstrInvoke(a, new Super());
		} else if (t.kind == 13) {
			Get();
			z = SuperMethodAccessTail(new Super(a));
		} else Error(118);
		return z;
	}

	private static Term UnaryWithPara() {
		Term z;
		Term b, d = null;
		// Slice 44: `(@Anno Type) expr` — type-use annotation on a cast.
		// Annotations are only legal on types in this position, so an
		// `@` here unambiguously means we're parsing an annotated cast
		// type. Skip the annotation; the rest of the type parses as a
		// name expression and is reinterpreted by UnaryWithParaTail.
		if (t.kind == 10) {
			TypeUseAnnotationGroup();
		}
		// Slice 41: parenthesized switch expression — `(switch(...){...})`.
		// Used as a grouping wrapper around a switch when it appears
		// inline in larger expressions (e.g. `"x" + (switch ...) + "y"`).
		// The hoister at the surrounding statement level still picks up
		// the inner SwitchExpression through the ParenExpression wrapper.
		if (t.kind == 53) {
			b = SwitchExpressionParse();
		} else {
			b = JavaExpression();
		}
		Expect(12);
		// Slice 38: also enter the cast-tail dispatcher when a `switch`
		// follows `(Type)` — `(long) switch (...) {...}`. The tail
		// itself routes kind-53 through SwitchExpressionParse.
		if (StartOf(6) || t.kind == 53) {
			d = UnaryWithParaTail(b);
		}
		z = d != null ? d : new ParenExpression(b);
		return z;
	}

	private static Term UnaryExpressionTail() {
		Term z;
		z = Empty.term;
		switch (t.kind) {
		case 98: {
			Get();
			z = new LexTerm(LexTerm.xNULL, token.val);
			break;
		}
		case 99: {
			Get();
			z = new LexTerm(LexTerm.FALSE, token.val);
			break;
		}
		case 100: {
			Get();
			z = new LexTerm(LexTerm.TRUE, token.val);
			break;
		}
		case 2: {
			Get();
			gateNumericLiteral(token.val);
			z = new IntLiteral(token.val);
			break;
		}
		case 3: {
			Get();
			gateNumericLiteral(token.val);
			z = new FloatLiteral(token.val);
			break;
		}
		case 4: {
			Get();
			z = new CharacterLiteral(token.val);
			break;
		}
		case 11: {
			Get();
			z = UnaryWithPara();
			break;
		}
		case 101: {
			Get();
			z = SuperConstrMethodAccess(Empty.term);
			break;
		}
		case 103: {
			z = ThisOptConstrMethodAccess();
			break;
		}
		case 35: case 36: case 37: case 38: case 39: case 40: case 41: case 42: {
			z = UnaryWithPrimitive();
			break;
		}
		case 5: case 34: case 102: {
			z = UnaryWithNewOrStr();
			break;
		}
		case 1: case 7: {
			z = UnaryWithIdent();
			break;
		}
		default: Error(119);
		}
		return z;
	}

	private static Term IncDecOp() {
		Term z;
		z = Empty.term;
		if (t.kind == 96) {
			Get();
			z = new LexTerm(LexTerm.INCREMENT, token.val);
		} else if (t.kind == 97) {
			Get();
			z = new LexTerm(LexTerm.DECREMENT, token.val);
		} else Error(120);
		return z;
	}

	private static Term NegatePlusMinusOp() {
		Term z;
		z = Empty.term;
		if (t.kind == 94) {
			Get();
			z = new LexTerm(LexTerm.NOT, token.val);
		} else if (t.kind == 95) {
			Get();
			z = new LexTerm(LexTerm.BITNOT, token.val);
		} else if (t.kind == 66) {
			Get();
			z = new LexTerm(LexTerm.PLUS, token.val);
		} else if (t.kind == 67) {
			Get();
			z = new LexTerm(LexTerm.MINUS, token.val);
		} else Error(121);
		return z;
	}

	private static Term AssignmentOperator() {
		Term z;
		z = Empty.term;
		switch (t.kind) {
		case 46: {
			Get();
			z = new LexTerm(LexTerm.EQUALS, token.val);
			break;
		}
		case 83: {
			Get();
			z = new LexTerm(LexTerm.TIMES_EQUALS, token.val);
			break;
		}
		case 84: {
			Get();
			z = new LexTerm(LexTerm.DIVIDE_EQUALS, token.val);
			break;
		}
		case 85: {
			Get();
			z = new LexTerm(LexTerm.MOD_EQUALS, token.val);
			break;
		}
		case 86: {
			Get();
			z = new LexTerm(LexTerm.PLUS_EQUALS, token.val);
			break;
		}
		case 87: {
			Get();
			z = new LexTerm(LexTerm.MINUS_EQUALS, token.val);
			break;
		}
		case 88: {
			Get();
			z = new LexTerm(LexTerm.SHLEFT_EQUALS, token.val);
			break;
		}
		case 89: {
			Get();
			z = new LexTerm(LexTerm.SHRIGHT_EQUALS, token.val);
			break;
		}
		case 90: {
			Get();
			z = new LexTerm(LexTerm.FLSHIFT_EQUALS, token.val);
			break;
		}
		case 91: {
			Get();
			z = new LexTerm(LexTerm.BITAND_EQUALS, token.val);
			break;
		}
		case 92: {
			Get();
			z = new LexTerm(LexTerm.XOR_EQUALS, token.val);
			break;
		}
		case 93: {
			Get();
			z = new LexTerm(LexTerm.BITOR_EQUALS, token.val);
			break;
		}
		default: Error(122);
		}
		return z;
	}

	private static Term EqualCompareOp() {
		Term z;
		z = Empty.term;
		if (t.kind == 76) {
			Get();
			z = new LexTerm(LexTerm.NE, token.val);
		} else if (t.kind == 77) {
			Get();
			z = new LexTerm(LexTerm.EQ, token.val);
		} else Error(123);
		return z;
	}

	private static Term RelCompareOp() {
		Term z;
		z = Empty.term;
		if (t.kind == 72) {
			Get();
			z = new LexTerm(LexTerm.LE, token.val);
		} else if (t.kind == 73) {
			Get();
			z = new LexTerm(LexTerm.LT, token.val);
		} else if (t.kind == 74) {
			Get();
			z = new LexTerm(LexTerm.GE, token.val);
		} else if (t.kind == 75) {
			Get();
			z = new LexTerm(LexTerm.GT, token.val);
		} else Error(124);
		return z;
	}

	private static Term RelCompareShiftExprSeq(Term a) {
		Term z;
		Term b, c, d = null;
		b = RelCompareOp();
		c = ShiftExpression();
		if (StartOf(7)) {
			d = RelCompareShiftExprSeq(new RelationalOp(a, b, c));
		}
		z = d != null ? d : new RelationalOp(a, b, c);
		
		return z;
	}

	private static Term InstanceOfTail(Term a) {
		Term z;
		Term c, d = Empty.term;
		if (t.kind == 1 && peek(2).kind == 11) {
			if (Main.dict.javaVersion < JavaVersion.JLS_210) {
				SemError("record patterns requires -source 21 or higher (got "
					+ JavaVersion.format(Main.dict.javaVersion) + ")");
			}
			RecordPattern rp = parseRecordPattern();
			InstanceOf io = new InstanceOf(a, rp.getType(), Empty.newTerm());
			io.setRecordPattern(rp);
			return io;
		}
		c = SimpleType();
		if (t.kind == 43) {
			d = DimSpecSeq();
		}
		InstanceOf io = new InstanceOf(a, c, d);
		// Pattern instanceof (Java 16): optional binding identifier.
		if (t.kind == 1 || t.kind == 7) {
			if (Main.dict.javaVersion < JavaVersion.JLS_160) {
				SemError("pattern instanceof requires -source 16 or higher (got "
					+ JavaVersion.format(Main.dict.javaVersion) + ")");
			}
			Get();
			io.setBindingName(token.val);
		}
		z = io;
		return z;
	}

	private static Term ShiftOp() {
		Term z;
		z = Empty.term;
		if (t.kind == 68) {
			Get();
			z = new LexTerm(LexTerm.SHIFT_LEFT, token.val);
		} else if (t.kind == 69) {
			Get();
			z = new LexTerm(LexTerm.FILLSHIFT_RIGHT, token.val);
		} else if (t.kind == 70) {
			Get();
			z = new LexTerm(LexTerm.SHIFT_RIGHT, token.val);
		} else Error(125);
		return z;
	}

	private static Term PlusMinusOp() {
		Term z;
		z = Empty.term;
		if (t.kind == 66) {
			Get();
			z = new LexTerm(LexTerm.PLUS, token.val);
		} else if (t.kind == 67) {
			Get();
			z = new LexTerm(LexTerm.MINUS, token.val);
		} else Error(126);
		return z;
	}

	private static Term ModMulDivOp() {
		Term z;
		z = Empty.term;
		if (t.kind == 64) {
			Get();
			z = new LexTerm(LexTerm.MOD, token.val);
		} else if (t.kind == 15) {
			Get();
			z = new LexTerm(LexTerm.TIMES, token.val);
		} else if (t.kind == 65) {
			Get();
			z = new LexTerm(LexTerm.DIVIDE, token.val);
		} else Error(127);
		return z;
	}

	private static Term ModMulDivUnaryExprSeq(Term a) {
		Term z;
		Term b, c, d = null;
		b = ModMulDivOp();
		c = UnaryExpression();
		if (t.kind == 15 || t.kind == 64 || t.kind == 65) {
			d = ModMulDivUnaryExprSeq(new BinaryOp(a, b, c));
		}
		z = d != null ? d : new BinaryOp(a, b, c);
		
		return z;
	}

	private static Term UnaryExpression() {
		Term z;
		Term a, b; z = Empty.term;
		if (StartOf(5)) {
			a = NegatePlusMinusOp();
			b = UnaryExpression();
			z = new UnaryExpression(a, b);
			
		} else if (StartOf(8)) {
			z = OptPrefixUnaryExpr();
		} else Error(128);
		return z;
	}

	private static Term PlusMinusMultiplicativeExprSeq(Term a) {
		Term z;
		Term b, c, d = null;
		b = PlusMinusOp();
		c = MultiplicativeExpression();
		if (t.kind == 66 || t.kind == 67) {
			d = PlusMinusMultiplicativeExprSeq(new BinaryOp(a, b, c));
		}
		z = d != null ? d : new BinaryOp(a, b, c);
		
		return z;
	}

	private static Term MultiplicativeExpression() {
		Term z;
		Term a, b = null;
		a = UnaryExpression();
		if (t.kind == 15 || t.kind == 64 || t.kind == 65) {
			b = ModMulDivUnaryExprSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term ShiftAdditiveExprSeq(Term a) {
		Term z;
		Term b, c, d = null;
		b = ShiftOp();
		c = AdditiveExpression();
		if (t.kind == 68 || t.kind == 69 || t.kind == 70) {
			d = ShiftAdditiveExprSeq(new BinaryOp(a, b, c));
		}
		z = d != null ? d : new BinaryOp(a, b, c);
		
		return z;
	}

	private static Term AdditiveExpression() {
		Term z;
		Term a, b = null;
		a = MultiplicativeExpression();
		// Slice 14a/15: do not eat `- >` as binary subtraction — it is the
		// `->` arrow in a switch-case label/guard or (future) lambda. Any
		// real Java `expr - expr` has a unary-expression-starter after the
		// `-`, never `>`.
		if ((t.kind == 66 || t.kind == 67)
				&& !(t.kind == 67 && peek(2).kind == 75)) {
			b = PlusMinusMultiplicativeExprSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term RelationalExpressionTail(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 71) {
			Get();
			z = InstanceOfTail(a);
		} else if (StartOf(7)) {
			z = RelCompareShiftExprSeq(a);
		} else Error(129);
		return z;
	}

	private static Term ShiftExpression() {
		Term z;
		Term a, b = null;
		a = AdditiveExpression();
		if (t.kind == 68 || t.kind == 69 || t.kind == 70) {
			b = ShiftAdditiveExprSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term EqualCompareRelationalExprSeq(Term a) {
		Term z;
		Term b, c, d = null;
		b = EqualCompareOp();
		c = RelationalExpression();
		if (t.kind == 76 || t.kind == 77) {
			d = EqualCompareRelationalExprSeq(new RelationalOp(a, b, c));
		}
		z = d != null ? d : new RelationalOp(a, b, c);
		
		return z;
	}

	private static Term RelationalExpression() {
		Term z;
		Term a, b = null;
		a = ShiftExpression();
		if (StartOf(9)) {
			b = RelationalExpressionTail(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term BitAndEqualityExpressionSeq(Term a) {
		Term z;
		Term b, c, d = null;
		Expect(78);
		b = new LexTerm(LexTerm.BITAND, token.val);
		c = EqualityExpression();
		if (t.kind == 78) {
			d = BitAndEqualityExpressionSeq(new BinaryOp(a, b, c));
		}
		z = d != null ? d : new BinaryOp(a, b, c);
		
		return z;
	}

	private static Term EqualityExpression() {
		Term z;
		Term a, b = null;
		a = RelationalExpression();
		if (t.kind == 76 || t.kind == 77) {
			b = EqualCompareRelationalExprSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term XorBitwiseAndExpressionSeq(Term a) {
		Term z;
		Term b, c, d = null;
		Expect(79);
		b = new LexTerm(LexTerm.XOR, token.val);
		c = BitwiseAndExpression();
		if (t.kind == 79) {
			d = XorBitwiseAndExpressionSeq(new BinaryOp(a, b, c));
		}
		z = d != null ? d : new BinaryOp(a, b, c);
		
		return z;
	}

	private static Term BitwiseAndExpression() {
		Term z;
		Term a, b = null;
		a = EqualityExpression();
		if (t.kind == 78) {
			b = BitAndEqualityExpressionSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term BitOrBitwiseXorExpressionSeq(Term a) {
		Term z;
		Term b, c, d = null;
		Expect(80);
		b = new LexTerm(LexTerm.BITOR, token.val);
		c = BitwiseXorExpression();
		if (t.kind == 80) {
			d = BitOrBitwiseXorExpressionSeq(new BinaryOp(a, b, c));
		}
		z = d != null ? d : new BinaryOp(a, b, c);
		
		return z;
	}

	private static Term BitwiseXorExpression() {
		Term z;
		Term a, b = null;
		a = BitwiseAndExpression();
		if (t.kind == 79) {
			b = XorBitwiseAndExpressionSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term AndBitwiseOrExpressionSeq(Term a) {
		Term z;
		Term b, c, d = null;
		Expect(81);
		b = new LexTerm(LexTerm.AND, token.val);
		c = BitwiseOrExpression();
		if (t.kind == 81) {
			d = AndBitwiseOrExpressionSeq(new CondOrAndOperation(a, b, c));
		}
		z = d != null ? d : new CondOrAndOperation(a, b, c);
		
		return z;
	}

	private static Term BitwiseOrExpression() {
		Term z;
		Term a, b = null;
		a = BitwiseXorExpression();
		if (t.kind == 80) {
			b = BitOrBitwiseXorExpressionSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term OrCondAndExpressionSeq(Term a) {
		Term z;
		Term b, c, d = null;
		Expect(82);
		b = new LexTerm(LexTerm.OR, token.val);
		c = CondAndExpression();
		if (t.kind == 82) {
			d = OrCondAndExpressionSeq(new CondOrAndOperation(a, b, c));
		}
		z = d != null ? d : new CondOrAndOperation(a, b, c);
		
		return z;
	}

	private static Term CondAndExpression() {
		Term z;
		Term a, b = null;
		a = BitwiseOrExpression();
		if (t.kind == 81) {
			b = AndBitwiseOrExpressionSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term ExpressionTail(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 58) {
			Get();
			z = CondExprTail(a);
		} else if (StartOf(10)) {
			z = AssignmentOpExpr(a);
		} else Error(130);
		return z;
	}

	private static Term OptForVarExprInitTailSemi(Term a) {
		Term z;
		Term b = null;
		if (StartOf(11)) {
			b = ForVarExprInitTail(a);
		}
		Expect(9);
		// Slice 14b: lift `T x = switch (...) {...};` into a Block.
		if (b != null) {
			Term lifted = SwitchExpressionLifter.tryLift(b);
			if (lifted != null) return lifted;
		}
		// Slice 22b: lift `lhs = switch (...) {...};` (no decl) into a
		// SwitchStatement that assigns to lhs in each arm.
		Term assignCandidate = b != null ? b : a;
		Term liftedAssign = SwitchExpressionLifter
			.tryLiftAssign(assignCandidate);
		if (liftedAssign != null) return liftedAssign;
		// Slice 36: hoist any switch-expression args inside method
		// calls (and other sub-expressions) out into preamble decls
		// + switch-stmts. Returns a Seq (not a Block) so any
		// LocalVariableDecl in the assignCandidate (e.g.
		// `T x = (T) switch(...);`) keeps its scope visible to the
		// enclosing block — Block would shadow it.
		SwitchArgHoister.Result hr = SwitchArgHoister.hoist(assignCandidate);
		if (hr.hoisted) {
			return new Seq(hr.preambles,
				new ExprStatement(hr.rewrittenRoot));
		}
		z = new ExprStatement(assignCandidate);
		return z;
	}

	private static Term ExprOrLabelStmntOrVarDeclTail(Term a) {
		Term z;
		Term c; z = Empty.term;
		if (t.kind == 57) {
			Get();
			c = JavaStatement();
			z = new LabeledStatement(a, c);
		} else if (StartOf(12)) {
			z = OptForVarExprInitTailSemi(a);
		} else Error(131);
		return z;
	}

	private static Term CatchClause() {
		Term z;
		Term c = Empty.term, d, e, h = Empty.term;
		Expect(63);
		Expect(11);
		if (t.kind == 20) {
			c = FinalModifier();
		}
		if (t.kind == 10) {
			AnnotationGroup();
		}
		d = QualifiedIdentifier();
		// Multi-catch (Java 7+): collect additional alternative types
		// separated by `|`. The whole clause is desugared to a chain of
		// single-type catches sharing the body Term — per JLS 14.20 each
		// catch is examined in source order, so the split is semantically
		// equivalent. Body code emits N times in C output (acceptable for
		// slice 6; refactor to a shared body when worth optimizing).
		ObjVector altTypes = null;
		while (t.kind == 80) {
			Get();
			if (Main.dict.javaVersion < JavaVersion.JLS_70) {
				SemError("multi-catch (|) requires -source 7 or higher (got "
					+ JavaVersion.format(Main.dict.javaVersion) + ")");
			}
			if (altTypes == null) altTypes = new ObjVector();
			altTypes.addElement(QualifiedIdentifier());
		}
		e = Identifier();
		Expect(12);
		Expect(28);
		if (StartOf(13)) {
			h = BlockStatementSeq();
		}
		Expect(29);
		z = new CatchStatement(c, new ClassOrIfaceType(d),
		     new VariableIdentifier(e), h);
		if (altTypes != null) {
			String idName = e.dottedName();
			for (int i = 0; i < altTypes.size(); i++) {
				Term altType = (Term) altTypes.elementAt(i);
				Term altCatch = new CatchStatement(c,
					new ClassOrIfaceType(altType),
					new VariableIdentifier(new LexTerm(LexTerm.ID, idName)),
					h);
				z = new CatchSeq(z, altCatch);
			}
		}

		return z;
	}

	private static Term CatchClauseSeq() {
		Term z;
		Term a, b = null;
		a = CatchClause();
		if (t.kind == 63) {
			b = CatchClauseSeq();
		}
		z = b != null ? new CatchSeq(a, b) : a;
		return z;
	}

	private static Term CatchClausesOptFinally(Term b) {
		Term z;
		Term c, e = Empty.term;
		c = CatchClauseSeq();
		if (t.kind == 62) {
			Get();
			e = JavaBlock();
		}
		z = new TryStatement(b, c, e);
		return z;
	}

	private static Term TryStatementTail(Term b) {
		Term z;
		Term d; z = Empty.term;
		if (t.kind == 62) {
			Get();
			d = JavaBlock();
			z = new TryStatement(b, d);
		} else if (t.kind == 63) {
			z = CatchClausesOptFinally(b);
		} else Error(132);
		return z;
	}

	private static Term ConditionalExpression() {
		Term z;
		Term a, b = null;
		a = CondOrExpression();
		if (t.kind == 58) {
			Get();
			b = CondExprTail(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term SwitchBlockStatementGroup() {
		Term z;
		Term b, c = Empty.term; z = Empty.term;

		if (t.kind == 60) {
			Get();
			if (looksLikeArrow()) {
				return parseArrowCaseTail(null);
			}
			Expect(57);
			if (StartOf(13)) {
				c = BlockStatementSeq();
			}
			z = new CaseStatement(c);
		} else if (t.kind == 61) {
			Get();
			// Heuristic: `case label,...` or `case label -> ...` is arrow
			// form. Use UnaryExpression so the `-` of the arrow isn't eaten
			// as binary subtraction. `case label : ...` is the existing
			// colon form and uses the wider ConditionalExpression to allow
			// `case 1+2:` etc.
			boolean arrowExpected = peek(2).kind == 27
				|| (peek(2).kind == 67 && peek(3).kind == 75);
			if (arrowExpected) {
				ObjVector labels = new ObjVector();
				labels.addElement(UnaryExpression());
				while (t.kind == 27) {
					Get();
					labels.addElement(UnaryExpression());
				}
				return parseArrowCaseTail(labels);
			}
			b = ConditionalExpression();
			Expect(57);
			if (StartOf(13)) {
				c = BlockStatementSeq();
			}
			z = new CaseStatement(new Expression(b), c);

		} else Error(133);
		return z;
	}

	// Switch expressions / arrow-case switch statements (Java 14, JEP 361).
	// Arrow form: `case L1, L2 -> stmt-or-block-or-expr;`. labels==null
	// means default arrow (`default -> ...`); else labels is the
	// non-empty list of case label expressions.
	private static boolean looksLikeArrow() {
		return t.kind == 67 && peek(2).kind == 75;
	}

	private static boolean looksLikeYield() {
		if (t.kind != 1 || !"yield".equals(t.val)) return false;
		if (Main.dict.javaVersion < JavaVersion.JLS_140) return false;
		int k2 = peek(2).kind;
		// Reject yield used as an identifier in plain assignment or call.
		return k2 != 46 && k2 != 11 && k2 != 13 && k2 != 27
			&& k2 != 43 && k2 != 96 && k2 != 97
			&& k2 != 9;
	}

	// Switch expression: `switch (E) { (case L1, L2 -> body | default -> body)+ }`
	private static Term SwitchExpressionParse() {
		if (Main.dict.javaVersion < JavaVersion.JLS_140) {
			SemError("switch expression requires -source 14 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Expect(53);
		Expect(11);
		Term discr = JavaExpression();
		Expect(12);
		Expect(28);
		ObjVector cases = new ObjVector();
		while (t.kind == 60 || t.kind == 61) {
			cases.addElement(parseSwitchExprCase());
		}
		Expect(29);
		Term casesChain = Empty.newTerm();
		for (int i = cases.size() - 1; i >= 0; i--) {
			Term cur = (Term) cases.elementAt(i);
			casesChain = casesChain.notEmpty()
				? new Seq(cur, casesChain) : cur;
		}
		return new SwitchExpression(new Expression(discr), casesChain);
	}

	private static Term parseSwitchExprCase() {
		Term labels;
		Term patternType = null;
		String patternBinding = null;
		RecordPattern recordPattern = null;
		Term guard = null;
		if (t.kind == 60) {
			Get();
			labels = Empty.newTerm();
		} else {
			Expect(61);
			// Pattern-case detection (Java 21): `case Type id [when ...] ->`.
			// Heuristic: identifier followed by identifier with no `,` or
			// `->` between them suggests a type-pattern label.
			if (t.kind == 1 && peek(2).kind == 1 && peek(3).kind != 27) {
				if (Main.dict.javaVersion < JavaVersion.JLS_210) {
					SemError("pattern switch requires -source 21 or higher (got "
						+ JavaVersion.format(Main.dict.javaVersion) + ")");
				}
				patternType = SimpleType();
				Term name = Identifier();
				patternBinding = name.dottedName();
				labels = Empty.newTerm();
				if (t.kind == 1 && "when".equals(t.val)) {
					Get();
					guard = JavaExpression();
				}
			} else if (t.kind == 1 && peek(2).kind == 11) {
				// Record pattern (Java 21, JEP 440): `case Type(...)`.
				if (Main.dict.javaVersion < JavaVersion.JLS_210) {
					SemError("record patterns requires -source 21 or higher (got "
						+ JavaVersion.format(Main.dict.javaVersion) + ")");
				}
				recordPattern = parseRecordPattern();
				labels = Empty.newTerm();
				if (t.kind == 1 && "when".equals(t.val)) {
					Get();
					guard = JavaExpression();
				}
			} else {
				ObjVector labelList = new ObjVector();
				labelList.addElement(UnaryExpression());
				while (t.kind == 27) {
					Get();
					labelList.addElement(UnaryExpression());
				}
				Term seq = Empty.newTerm();
				for (int i = labelList.size() - 1; i >= 0; i--) {
					Term cur = (Term) labelList.elementAt(i);
					seq = seq.notEmpty() ? new Seq(cur, seq) : cur;
				}
				labels = seq;
			}
		}
		if (!looksLikeArrow()) {
			SemError("'->' expected in switch expression case");
		}
		Get(); Get();
		Term body;
		int bodyKind;
		if (t.kind == 28) {
			body = JavaBlock();
			bodyKind = SwitchExprArrowCase.BODY_BLOCK;
		} else if (t.kind == 54) {
			Get();
			body = ThrowStatement();
			bodyKind = SwitchExprArrowCase.BODY_THROW;
		} else {
			body = JavaExpression();
			Expect(9);
			bodyKind = SwitchExprArrowCase.BODY_EXPR;
		}
		SwitchExprArrowCase result = new SwitchExprArrowCase(labels, body,
			bodyKind);
		if (patternType != null) {
			result.setPattern(patternType, patternBinding, guard);
		} else if (recordPattern != null) {
			result.setRecordPattern(recordPattern, guard);
		}
		return result;
	}

	// Slice 17 (Java 7): version-gate numeric literals containing
	// underscores or the `0b`/`0B` binary prefix. The scanner accepts
	// these unconditionally; gating at parse time is cheaper than
	// threading version state into Scanner.
	private static void gateNumericLiteral(String val) {
		if (Main.dict.javaVersion >= JavaVersion.JLS_70) return;
		boolean hasUnderscore = val.indexOf('_') >= 0;
		boolean isBinary = val.length() > 1 && val.charAt(0) == '0'
			&& (val.charAt(1) == 'b' || val.charAt(1) == 'B');
		if (hasUnderscore) {
			SemError("numeric literal underscore requires -source 7 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		if (isBinary) {
			SemError("binary integer literal requires -source 7 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
	}

	// Slice 16: parses a record pattern. Caller has already verified the
	// next two tokens form `Type (`. Result captures the type plus a
	// positional list of sub-patterns; component name lookup happens at
	// desugar time via RecordSynthesis.componentsByName.
	private static RecordPattern parseRecordPattern() {
		Term type = SimpleType();
		Expect(11);
		ObjVector components = new ObjVector();
		if (t.kind != 12) {
			components.addElement(parseRecordPatternComponent());
			while (t.kind == 27) {
				Get();
				components.addElement(parseRecordPatternComponent());
			}
		}
		Expect(12);
		return new RecordPattern(type, components);
	}

	private static RecordPattern.Component parseRecordPatternComponent() {
		// Sub-pattern is one of:
		//   `var id`         — type comes from the record's component
		//   `Type id`        — explicit binding type
		//   `Type ( ... )`   — nested record pattern
		// The `var` form leaves bindingType null; the lifter fills it in
		// from the enclosing record's componentsByName entry.
		if (t.kind == 1 && "var".equals(t.val) && peek(2).kind == 1) {
			Get();
			Term name = Identifier();
			return new RecordPattern.Component(null, name.dottedName());
		}
		if (t.kind == 1 && peek(2).kind == 11) {
			RecordPattern nested = parseRecordPattern();
			return new RecordPattern.Component(nested);
		}
		Term type = SimpleType();
		Term name = Identifier();
		return new RecordPattern.Component(type, name.dottedName());
	}

	private static Term parseArrowCaseTail(ObjVector labels) {
		if (Main.dict.javaVersion < JavaVersion.JLS_140) {
			SemError("arrow case form requires -source 14 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Get(); Get();
		Term body;
		if (t.kind == 28) {
			body = JavaBlock();
		} else if (t.kind == 54) {
			Get();
			body = ThrowStatement();
		} else {
			Term expr = JavaExpression();
			Expect(9);
			body = new ExprStatement(expr);
		}
		// Append synthetic break so the case doesn't fall through.
		Term bodyWithBreak = new Seq(body,
			new BreakStatement(Empty.newTerm()));
		// Build chain: for multi-label, all but last get empty bodies;
		// last (or default) gets the body+break.
		if (labels == null) {
			return new CaseStatement(bodyWithBreak);
		}
		int n = labels.size();
		Term result = new CaseStatement(
			new Expression((Term) labels.elementAt(n - 1)), bodyWithBreak);
		for (int i = n - 2; i >= 0; i--) {
			Term emptyBodyCase = new CaseStatement(
				new Expression((Term) labels.elementAt(i)),
				Empty.newTerm());
			result = new Seq(emptyBodyCase, result);
		}
		return result;
	}

	private static Term SwitchBlockStatementGroupSeq() {
		Term z;
		Term a, b = null;
		a = SwitchBlockStatementGroup();
		if (t.kind == 60 || t.kind == 61) {
			b = SwitchBlockStatementGroupSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term OptPrefixUnaryExpr() {
		Term z;
		Term a = null, b;
		if (t.kind == 96 || t.kind == 97) {
			a = IncDecOp();
		}
		b = UnaryExpressionTail();
		z = a != null ? new PrefixOp(a, b) : b;
		return z;
	}

	private static Term AssignmentOpExpr(Term a) {
		Term z;
		Term b, c;
		b = AssignmentOperator();
		// Slice 22b: allow `lhs = switch (...) {...}`. Lift happens at
		// the statement parser site (OptForVarExprInitTailSemi) where
		// the result becomes a bare SwitchStatement.
		if (t.kind == 53) {
			c = SwitchExpressionParse();
		} else {
			c = JavaExpression();
		}
		z = new Assignment(a, b, c);
		return z;
	}

	private static Term CondExprTail(Term a) {
		Term z;
		Term c, e;
		// Slice 39: lambda / method-ref in ternary arms. Both arms can
		// be lambdas (`cond ? () -> x : () -> y`) — the surrounding
		// context (variable initializer, return, cast, ...) provides
		// the target functional interface via c.currentVarType, and
		// CondExpression's processPass1 propagates it to both arms.
		c = parseTernaryArm(true);
		Expect(57);
		e = parseTernaryArm(false);
		z = new CondExpression(a, c, e);
		return z;
	}

	private static Term parseTernaryArm(boolean isThen) {
		if (looksLikeLambda()) return LambdaParse();
		if (looksLikeMethodRef()) return MethodRefParse();
		// Slice 43: switch-expression as a ternary arm. The hoister
		// at the surrounding statement level walks the resulting
		// CondExpression and lifts the SwitchExpression.
		if (t.kind == 53) return SwitchExpressionParse();
		return isThen ? JavaExpression() : ConditionalExpression();
	}

	private static Term StatementExpression() {
		Term z;
		Term a, b = null;
		a = OptPrefixUnaryExpr();
		if (StartOf(10)) {
			b = AssignmentOpExpr(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term CommaExprOrExprTail(Term a) {
		Term z;
		Term c; z = Empty.term;
		if (t.kind == 27) {
			Get();
			c = StatementExpression();
			z = new ExpressionList(a, c);
		} else if (t.kind == 58) {
			Get();
			z = CondExprTail(a);
		} else if (StartOf(10)) {
			z = AssignmentOpExpr(a);
		} else Error(134);
		return z;
	}

	private static Term ForExprOnlyInitTail(Term a) {
		Term z;
		Term a2, c = null;
		a2 = CommaExprOrExprTail(a);
		if (t.kind == 27) {
			Get();
			c = ExpressionList();
		}
		z = c != null ? new ExpressionList(a2, c) : a2;
		
		return z;
	}

	private static Term ForVarExprInitTail(Term a) {
		Term z;
		z = Empty.term; Term b;
		if (t.kind == 1 || t.kind == 7) {
			b = VariableDeclaratorList();
			z = new LocalVariableDecl(a, b);
		} else if (StartOf(14)) {
			z = ForExprOnlyInitTail(a);
		} else Error(135);
		return z;
	}

	private static Term CondOrExpression() {
		Term z;
		Term a, b = null;
		a = CondAndExpression();
		if (t.kind == 82) {
			b = OrCondAndExpressionSeq(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term ForVarExprInit() {
		Term z;
		Term a, b = null;
		if (t.kind == 10) {
			AnnotationGroup();
		}
		a = CondOrExpression();
		if (StartOf(11)) {
			b = ForVarExprInitTail(a);
		}
		z = b != null ? b : a;
		return z;
	}

	private static Term ForFinalInit() {
		Term z;
		Term b, c = Empty.term, d;
		if (t.kind == 10) {
			AnnotationGroup();
		}
		b = SimpleType();
		if (t.kind == 43) {
			c = DimSpecSeq();
		}
		d = VariableDeclaratorList();
		z = new LocalVariableDecl(new AccModifier(AccModifier.FINAL), b, c, d);
		
		return z;
	}

	private static Term ExpressionList() {
		Term z;
		Term a, c = null;
		a = StatementExpression();
		if (t.kind == 27) {
			Get();
			c = ExpressionList();
		}
		z = c != null ? new ExpressionList(a, c) : a;
		return z;
	}

	private static Term ForInit() {
		Term z;
		z = Empty.term;
		if (t.kind == 20) {
			Get();
			z = ForFinalInit();
		} else if (StartOf(15)) {
			z = ForVarExprInit();
		} else Error(136);
		return z;
	}

	private static Term ExprOrLabeledStmntOrVarDecl() {
		Term z;
		// Slice 24: detect a generic-typed local var declaration up front
		// so `Box<Integer> b = ...;` doesn't get mis-parsed as the
		// nested-relational `(Box < Integer) > b` chain. Without the
		// lookahead the angle brackets eat as comparison operators and
		// pass1 then fails to resolve `Box` as a variable.
		if (looksLikeGenericVarDecl()) {
			Term type = SimpleType();
			Term declrs = VariableDeclaratorList();
			Expect(9);
			return new ExprStatement(new LocalVariableDecl(type, declrs));
		}
		Term a;
		a = CondOrExpression();
		z = ExprOrLabelStmntOrVarDeclTail(a);
		return z;
	}

	private static boolean looksLikeGenericVarDecl() {
		if (Main.dict.javaVersion < JavaVersion.JLS_50) return false;
		if (t.kind != 1 && t.kind != 7) return false;
		int idx = 2;
		while (peek(idx).kind == 13 && peek(idx + 1).kind == 1) {
			idx += 2;
		}
		if (peek(idx).kind != 73) return false;  // no `<`
		idx++;
		int depth = 1;
		while (depth > 0) {
			Token tk = peek(idx);
			if (tk == null || tk.kind == 0) return false;
			int k = tk.kind;
			if (k == 73) depth++;
			else if (k == 75) depth--;
			else if (k == 70) {
				if (depth < 2) return false;
				depth -= 2;
			}
			else if (k == 69) {
				if (depth < 3) return false;
				depth -= 3;
			}
			else if (k == 9 || k == 28 || k == 29) return false;
			idx++;
			// Bound the search so a runaway scan doesn't burn the
			// whole file; 256 tokens is plenty for any realistic
			// generic prefix.
			if (idx > 256) return false;
		}
		return peek(idx).kind == 1;
	}

	private static Term WhileStatement() {
		Term z;
		Term c, e;
		Expect(11);
		c = JavaExpression();
		Expect(12);
		e = JavaStatement();
		z = new WhileStatement(c, e);
		return z;
	}

	private static Term TryStatement() {
		Term z;
		Term b;
		if (t.kind == 11) {
			z = TryWithResources();
			return z;
		}
		b = JavaBlock();
		z = TryStatementTail(b);
		return z;
	}

	// try-with-resources (Java 7, JLS 14.20.3). Slice 13 MVP: only the
	// `try (resources) body` form. catch/finally combinations and var
	// resources are not yet supported.
	private static Term TryWithResources() {
		if (Main.dict.javaVersion < JavaVersion.JLS_70) {
			SemError("try-with-resources requires -source 7 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Expect(11);
		ObjVector resources = new ObjVector();
		while (true) {
			Term resource = parseTwrResource();
			resources.addElement(resource);
			if (t.kind == 9) {
				Get();
				if (t.kind == 12) break;
			} else if (t.kind == 12) {
				break;
			} else {
				Error(11);
				break;
			}
		}
		Expect(12);
		Term body = JavaBlock();
		if (t.kind == 62 || t.kind == 63) {
			SemError("try-with-resources combined with catch/finally is not yet implemented");
		}
		return buildTwrDesugar(resources, body);
	}

	// Each resource: [final] Type Identifier = Expression
	// Java 9 (slice 27) also accepts an existing effectively-final
	// variable as the resource — `try (existingVar; ...) { ... }`.
	// Detection: bare identifier followed by `;` or `)` (no `=`).
	private static Term parseTwrResource() {
		if (t.kind == 1 && (peek(2).kind == 9 || peek(2).kind == 12)) {
			if (Main.dict.javaVersion < JavaVersion.JLS_90) {
				SemError("try-with-resources on an existing variable "
					+ "requires -source 9 or higher (got "
					+ JavaVersion.format(Main.dict.javaVersion) + ")");
			}
			String name = t.val;
			Get();
			return new ExprStatement(new Expression(new QualifiedName(
				new LexTerm(LexTerm.ID, name), Empty.newTerm())));
		}
		boolean isFinal = false;
		if (t.kind == 20) {
			Get();
			isFinal = true;
		}
		Term type = SimpleType();
		Term name = Identifier();
		Expect(46);
		Term init = JavaExpression();
		Term varDeclr = new VariableDeclarator(
			new VariableIdentifier(name), Empty.newTerm(), init);
		Term decl = isFinal
			? new LocalVariableDecl(new AccModifier(AccModifier.FINAL),
				type, Empty.newTerm(), varDeclr)
			: new LocalVariableDecl(type, varDeclr);
		return new ExprStatement(decl);
	}

	private static Term buildTwrDesugar(ObjVector resources, Term body) {
		Term tryBody = body;
		// Build close calls in reverse declaration order.
		Term closeSeq = Empty.newTerm();
		for (int i = resources.size() - 1; i >= 0; i--) {
			Term resource = (Term) resources.elementAt(i);
			String resName = extractTwrResourceName(resource);
			if (resName == null) continue;
			Term receiver = new Expression(new QualifiedName(
				new LexTerm(LexTerm.ID, resName), Empty.newTerm()));
			Term call = new MethodInvocation(receiver,
				new LexTerm(LexTerm.ID, "close"), Empty.newTerm());
			Term stmt = new ExprStatement(call);
			closeSeq = closeSeq.notEmpty()
				? new Seq(stmt, closeSeq) : stmt;
		}
		Term finallyBlock = new Block(closeSeq);
		Term wrappedTry = new TryStatement(tryBody, finallyBlock);
		// Wrap resources + try in a Block. Existing-variable resources
		// (Java 9 slice 27) skip the prelude — the variable is already
		// in scope and re-emitting `varName;` would just be a useless
		// expression statement.
		Term blockSeq = wrappedTry;
		for (int i = resources.size() - 1; i >= 0; i--) {
			Term resource = (Term) resources.elementAt(i);
			if (isTwrExistingVarResource(resource)) continue;
			blockSeq = new Seq(resource, blockSeq);
		}
		return new Block(blockSeq);
	}

	private static boolean isTwrExistingVarResource(Term resourceStmt) {
		if (!(resourceStmt instanceof ExprStatement)) return false;
		Term inner = ((ExprStatement) resourceStmt).terms[0];
		return inner instanceof Expression;
	}

	private static String extractTwrResourceName(Term resourceStmt) {
		if (resourceStmt instanceof ExprStatement) {
			Term inner = ((ExprStatement) resourceStmt).terms[0];
			// Java 9 existing-variable form: ExprStatement(Expression(QN)).
			if (inner instanceof Expression) {
				Term qn = ((Expression) inner).terms[0];
				if (qn != null) return qn.dottedName();
			}
			if (inner instanceof LocalVariableDecl) {
				LocalVariableDecl lvd = (LocalVariableDecl) inner;
				Term decl = lvd.terms[3];
				if (decl instanceof VariableDeclarator) {
					Term vid = ((VariableDeclarator) decl).terms[0];
					if (vid instanceof VariableIdentifier) {
						return ((VariableIdentifier) vid).terms[0]
							.dottedName();
					}
				}
			}
		}
		return null;
	}

	private static Term ThrowStatement() {
		Term z;
		Term b;
		// Slice 31: `throw switch (...) {...};` lifts to a switch
		// statement where each arm is itself a throw of the arm's
		// expression — no temp needed.
		// Slice 37: pattern-switch arms route through a $matched-flag
		// chain (liftPatternThrowSwitch).
		if (t.kind == 53) {
			Term se = SwitchExpressionParse();
			Expect(9);
			if (se instanceof SwitchExpression) {
				SwitchExpression sw = (SwitchExpression) se;
				if (SwitchExpressionLifter.anyPatternCases(sw)) {
					return SwitchExpressionLifter
						.liftPatternThrowSwitch(sw);
				}
				return SwitchExpressionLifter.liftThrowSwitch(sw);
			}
			return new ThrowStatement(se);
		}
		b = JavaExpression();
		Expect(9);
		// Slice 36: hoist switch-expression args inside the throw
		// expression (e.g. `throw new RE("x:" + switch(x){...}));`).
		SwitchArgHoister.Result hr = SwitchArgHoister.hoist(b);
		if (hr.hoisted) {
			return new Block(new Seq(hr.preambles,
				new ThrowStatement(hr.rewrittenRoot)));
		}
		z = new ThrowStatement(b);
		return z;
	}

	private static Term SynchronizedStatement() {
		Term z;
		Term c, e;
		Expect(11);
		c = JavaExpression();
		Expect(12);
		e = JavaBlock();
		z = new SynchroStatement(c, e);
		return z;
	}

	private static Term SwitchStatement() {
		Term z;
		Term c, f = Empty.term;
		Expect(11);
		c = JavaExpression();
		Expect(12);
		Expect(28);
		if (t.kind == 60 || t.kind == 61) {
			f = SwitchBlockStatementGroupSeq();
		}
		Expect(29);
		z = new SwitchStatement(c, f);
		return z;
	}

	private static Term ReturnStatement() {
		Term z;
		Term b = Empty.term;
		// Slice 22: allow `return switch (...) {...};`. SwitchExpression
		// parsing is gated to JLS 14+ via parseSwitchExprCase's checks;
		// the actual lift happens at pass1 in ReturnStatement.processPass1
		// where the method's return type is available.
		// Slice 24d: also accept lambdas and method references at the
		// start of the return expression, since both need an explicit
		// target type which the surrounding method's declared return
		// type provides at pass1.
		if (t.kind == 53) {
			b = SwitchExpressionParse();
		} else if (looksLikeLambda()) {
			b = LambdaParse();
		} else if (looksLikeMethodRef()) {
			b = MethodRefParse();
		} else if (StartOf(1)) {
			b = JavaExpression();
		}
		Expect(9);
		// Slice 36: hoist switch-expression args inside the return
		// expression (e.g. `return f(switch(x){...});`).
		SwitchArgHoister.Result hr = SwitchArgHoister.hoist(b);
		if (hr.hoisted) {
			return new Block(new Seq(hr.preambles,
				new ReturnStatement(hr.rewrittenRoot)));
		}
		z = new ReturnStatement(b);
		return z;
	}

	private static Term IfThenOptElseStatement() {
		Term z;
		Term c, e, g = Empty.term;
		Expect(11);
		c = JavaExpression();
		Expect(12);
		e = JavaStatement();
		if (t.kind == 59) {
			Get();
			g = JavaStatement();
		}
		z = new IfThenElse(c, e, g);
		return z;
	}

	private static Term ForStatement() {
		Term z;
		Term c = Empty.term, e = Empty.term, g = Empty.term, i;

		Expect(11);
		// Foreach detection (slice 1): SimpleType Identifier ':' ...
		// Slice 1 supports only primitives or unqualified single-identifier
		// types, no 'final', no annotations, no array dims on the loop var.
		// The classic-for path catches everything else.
		if (looksLikeForeach()) {
			Term ftype = SimpleType();
			Term fident = Identifier();
			Expect(57);
			Term fiter = JavaExpression();
			Expect(12);
			Term fbody = JavaStatement();
			return new ForeachStatement(ftype, new VariableIdentifier(fident),
				fiter, fbody);
		}
		if (StartOf(16)) {
			c = ForInit();
		}
		Expect(9);
		if (StartOf(1)) {
			e = JavaExpression();
		}
		Expect(9);
		if (StartOf(8)) {
			g = ExpressionList();
		}
		Expect(12);
		i = JavaStatement();
		z = new ForStatement(c, e, g, i);
		return z;
	}

	// Foreach lookahead helper (slice 1).
	private static boolean looksLikeForeach() {
		int k1 = t.kind;
		boolean prim = k1 >= 35 && k1 <= 42;
		boolean ident = k1 == 1 || k1 == 7;
		if (!prim && !ident) return false;
		Token p2 = peek(2);
		if (p2.kind != 1 && p2.kind != 7) return false;
		return peek(3).kind == 57;
	}

	private static Term DoStatement() {
		Term z;
		Term b, e;
		b = JavaStatement();
		Expect(56);
		Expect(11);
		e = JavaExpression();
		Expect(12);
		Expect(9);
		z = new DoStatement(b, e);
		return z;
	}

	private static Term ContinueStatement() {
		Term z;
		Term b = Empty.term;
		if (t.kind == 1 || t.kind == 7) {
			b = Identifier();
		}
		Expect(9);
		z = new ContinueStatement(b);
		return z;
	}

	private static Term BreakStatement() {
		Term z;
		Term b = Empty.term;
		if (t.kind == 1 || t.kind == 7) {
			b = Identifier();
		}
		Expect(9);
		z = new BreakStatement(b);
		return z;
	}

	private static Term AssertionStatement() {
		Term z;
		Term b, d = Empty.term;
		b = JavaExpression();
		if (t.kind == 57) {
			Get();
			d = JavaExpression();
			d = new Argument(d);
		}
		Expect(9);
		z = new AssertionStatement(b, d);
		return z;
	}

	private static Term AbstractOrStaticOrStrict() {
		Term z;
		z = Empty.term;
		if (t.kind == 21) {
			Get();
			z = new AccModifier(AccModifier.ABSTRACT);
		} else if (t.kind == 19) {
			Get();
			z = new AccModifier(AccModifier.STATIC);
		} else if (t.kind == 22) {
			Get();
			z = new AccModifier(AccModifier.STRICT);
		} else Error(137);
		return z;
	}

	private static Term LocalClassModifiersNoFinal() {
		Term z;
		Term a, b = null;
		a = AbstractOrStaticOrStrict();
		if (t.kind == 20 || t.kind == 21 || t.kind == 22) {
			b = LocalClassModifierSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term LocalClassModifier() {
		Term z;
		z = Empty.term;
		if (t.kind == 20) {
			Get();
			z = new AccModifier(AccModifier.FINAL);
		} else if (t.kind == 21) {
			Get();
			z = new AccModifier(AccModifier.ABSTRACT);
		} else if (t.kind == 22) {
			Get();
			z = new AccModifier(AccModifier.STRICT);
		} else Error(138);
		return z;
	}

	private static Term LocalClassModifierSeq() {
		Term z;
		Term a, b = null;
		a = LocalClassModifier();
		if (t.kind == 20 || t.kind == 21 || t.kind == 22) {
			b = LocalClassModifierSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term FinalLocalVarDeclTail() {
		Term z;
		Term b, c = Empty.term, d;
		b = SimpleType();
		if (t.kind == 43) {
			c = DimSpecSeq();
		}
		d = VariableDeclaratorList();
		Expect(9);
		Term lvd = new LocalVariableDecl(
		     new AccModifier(AccModifier.FINAL), b, c, d);
		Term lifted = SwitchExpressionLifter.tryLift(lvd);
		if (lifted != null) return lifted;
		z = new ExprStatement(lvd);

		return z;
	}

	private static Term OptModifiersLocalClassDecl() {
		Term z;
		Term a = null, b;
		if (t.kind == 20 || t.kind == 21 || t.kind == 22) {
			a = LocalClassModifierSeq();
		}
		Expect(23);
		b = ClassDeclaration();
		z = new TypeDeclaration(a != null ?
		     new Seq(new AccModifier(AccModifier.FINAL), a) :
		     (Term) (new AccModifier(AccModifier.FINAL)), b);
		
		return z;
	}

	private static Term JavaStatement() {
		Term z;
		z = Empty.term;
		// Slice 44: leading annotation on a statement-level local var
		// (`@Anno T x = ...;`). Skip the annotation group and
		// re-dispatch to JavaStatement.
		if (t.kind == 10) {
			AnnotationGroup();
			return JavaStatement();
		}
		switch (t.kind) {
		case 9: {
			Get();
			z = new ExprStatement();
			break;
		}
		case 28: {
			z = JavaBlock();
			break;
		}
		case 7: {
			Get();
			z = AssertionStatement();
			break;
		}
		case 47: {
			Get();
			z = BreakStatement();
			break;
		}
		case 48: {
			Get();
			z = ContinueStatement();
			break;
		}
		case 49: {
			Get();
			z = DoStatement();
			break;
		}
		case 50: {
			Get();
			z = ForStatement();
			break;
		}
		case 51: {
			Get();
			z = IfThenOptElseStatement();
			break;
		}
		case 52: {
			Get();
			z = ReturnStatement();
			break;
		}
		case 53: {
			Get();
			z = SwitchStatement();
			break;
		}
		case 30: {
			Get();
			z = SynchronizedStatement();
			break;
		}
		case 54: {
			Get();
			z = ThrowStatement();
			break;
		}
		case 55: {
			Get();
			z = TryStatement();
			break;
		}
		case 56: {
			Get();
			z = WhileStatement();
			break;
		}
		case 1: case 2: case 3: case 4: case 5: /* case 7: */ case 11: case 34: case 35: case 36: case 37: case 38: case 39: case 40: case 41: case 42: case 66: case 67: case 94: case 95: case 96: case 97: case 98: case 99: case 100: case 101: case 102: case 103: {
			if (looksLikeYield()) {
				Get();
				Term ye = JavaExpression();
				Expect(9);
				z = new YieldStatement(ye);
				break;
			}
			z = ExprOrLabeledStmntOrVarDecl();
			break;
		}
		default: Error(139);
		}
		return z;
	}

	private static Term ModifiersLocClassDeclNoFinal() {
		Term z;
		Term a = Empty.term, b;
		if (t.kind == 19 || t.kind == 21 || t.kind == 22) {
			a = LocalClassModifiersNoFinal();
		}
		Expect(23);
		b = ClassDeclaration();
		z = new TypeDeclaration(a, b);
		return z;
	}

	private static Term FinalClsDeclOrVarDeclStmtTail() {
		Term z;
		z = Empty.term;
		if (StartOf(17)) {
			z = OptModifiersLocalClassDecl();
		} else if (StartOf(18)) {
			z = FinalLocalVarDeclTail();
		} else Error(140);
		return z;
	}

	private static Term VariableDeclarator() {
		Term z;
		Term a, b = Empty.term, d = Empty.term;
		a = Identifier();
		if (t.kind == 43) {
			b = DimSpecSeq();
		}
		if (t.kind == 46) {
			Get();
			d = VariableInitializer();
		}
		z = new VariableDeclarator(new VariableIdentifier(a), b, d);
		
		return z;
	}

	private static Term ArrayInitializerList() {
		Term z;
		Term a, c = null;
		a = VariableInitializer();
		if (t.kind == 27) {
			Get();
			if (StartOf(19)) {
				c = ArrayInitializerList();
			}
		}
		z = c != null ? (Term) (new VarInitializers(new ArrElementInit(a), c)) :
		     new ArrElementInit(a);
		
		return z;
	}

	private static Term JavaExpression() {
		Term z;
		Term a, b = null;
		a = CondOrExpression();
		if (StartOf(20)) {
			b = ExpressionTail(a);
		}
		z = new Expression(b != null ? b : a);
		return z;
	}

	private static Term ArrayInitializer() {
		Term z;
		Term b = Empty.term;
		Expect(28);
		if (StartOf(19)) {
			b = ArrayInitializerList();
		}
		Expect(29);
		z = new ArrayInitializer(b);
		return z;
	}

	private static Term VariableDeclaratorList() {
		Term z;
		Term a, c = null;
		a = VariableDeclarator();
		if (t.kind == 27) {
			Get();
			c = VariableDeclaratorList();
		}
		z = c != null ? new VariableDeclareList(a, c) : a;
		
		return z;
	}

	private static Term VariableInitializer() {
		Term z;
		z = Empty.term;
		if (t.kind == 28) {
			z = ArrayInitializer();
		} else if (t.kind == 53) {
			z = SwitchExpressionParse();
		} else if (looksLikeLambda()) {
			z = LambdaParse();
		} else if (looksLikeMethodRef()) {
			z = MethodRefParse();
		} else if (StartOf(1)) {
			z = JavaExpression();
		} else Error(141);
		return z;
	}

	// Slice 23c (Java 8): method reference detection.
	//   id (.id)* :: id          (e.g. Integer::parseInt, System.out::println)
	//   id (.id)* :: new         (e.g. Foo::new)
	// Slice 24c grew the receiver to a dotted chain.
	private static boolean looksLikeMethodRef() {
		if (t.kind != 1) return false;
		// Walk past id (.id)* to find `::`.
		int idx = 2;
		while (peek(idx).kind == 13 && peek(idx + 1).kind == 1) {
			idx += 2;
		}
		if (peek(idx).kind != 57 || peek(idx + 1).kind != 57) return false;
		int kAfter = peek(idx + 2).kind;
		return kAfter == 1 || kAfter == 102;
	}

	private static Term MethodRefParse() {
		if (Main.dict.javaVersion < JavaVersion.JLS_80) {
			SemError("method reference requires -source 8 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		// Collect dotted-id chain into a QualifiedName; first segment
		// is the outermost. For `System.out::println` the chain is
		// QualifiedName("System", QualifiedName("out", Empty)).
		ObjVector segs = new ObjVector();
		segs.addElement(new LexTerm(LexTerm.ID, t.val));
		Get();
		while (t.kind == 13 && peek(2).kind == 1) {
			Get();  // `.`
			segs.addElement(new LexTerm(LexTerm.ID, t.val));
			Get();  // id
		}
		Term qn = Empty.newTerm();
		for (int i = segs.size() - 1; i >= 0; i--) {
			qn = qn.notEmpty()
				? new QualifiedName((Term) segs.elementAt(i), qn)
				: new QualifiedName((Term) segs.elementAt(i),
					Empty.newTerm());
		}
		Term receiver = new Expression(qn);
		Expect(57);     // `:`
		Expect(57);     // `:`
		boolean isCtor = false;
		String methodName;
		if (t.kind == 102) {
			isCtor = true;
			methodName = "<init>";
			Get();
		} else {
			if (t.kind != 1) {
				SemError("expected method name after ::");
				methodName = "?";
			} else {
				methodName = t.val;
				Get();
			}
		}
		return new MethodReference(receiver, methodName, isCtor);
	}

	// Slice 23 / 25: lambda detection.
	//   id -> body                            (3-token peek)
	//   () -> body                            (4-token peek)
	//   (anything) -> body                    (variable-depth peek)
	// The (anything) form covers untyped params `(a, b)`, typed params
	// `(int x, String s)`, and var-typed params `(var x)` — anything
	// the LambdaParse param parser is willing to consume.
	private static boolean looksLikeLambda() {
		if (t.kind == 11 && peek(2).kind == 12 && peek(3).kind == 67
				&& peek(4).kind == 75) {
			return true;
		}
		if (t.kind == 1 && peek(2).kind == 67 && peek(3).kind == 75) {
			return true;
		}
		if (t.kind == 11) {
			// Walk balanced parens to find the matching `)`, then check
			// the two tokens after it for `->`.
			int idx = 2;
			int depth = 1;
			while (depth > 0) {
				Token tk = peek(idx);
				if (tk == null || tk.kind == 0) return false;
				if (tk.kind == 11) depth++;
				else if (tk.kind == 12) depth--;
				// Bail at statement-level boundaries — avoids burning
				// peek depth scanning through arbitrary code.
				else if (tk.kind == 28 || tk.kind == 9) return false;
				idx++;
				if (idx > 256) return false;
			}
			return peek(idx).kind == 67 && peek(idx + 1).kind == 75;
		}
		return false;
	}

	private static Term LambdaParse() {
		if (Main.dict.javaVersion < JavaVersion.JLS_80) {
			SemError("lambda expression requires -source 8 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Term params;
		if (t.kind == 11) {
			Get();          // `(`
			if (t.kind == 12) {
				params = Empty.newTerm();
			} else {
				ObjVector ids = new ObjVector();
				parseLambdaParam(ids);
				while (t.kind == 27) {
					Get();
					parseLambdaParam(ids);
				}
				params = Empty.newTerm();
				for (int i = ids.size() - 1; i >= 0; i--) {
					Term lt = (Term) ids.elementAt(i);
					params = params.notEmpty() ? new Seq(lt, params) : lt;
				}
			}
			Expect(12);     // `)`
		} else {
			// Single unparenthesized identifier param.
			params = new LexTerm(LexTerm.ID, t.val);
			Get();
		}
		Expect(67);          // `-`
		Expect(75);          // `>`
		Term body;
		boolean bodyIsBlock = false;
		if (t.kind == 28) {
			body = JavaBlock();
			bodyIsBlock = true;
		} else {
			body = JavaExpression();
		}
		return new LambdaExpression(params, body, bodyIsBlock);
	}

	// Slice 25/26: parse one lambda parameter into `ids`. Forms:
	//   id              — untyped, type comes from SAM
	//   Type id         — explicit typed (Type erased to whatever
	//                     SimpleType produces; the lifter still uses
	//                     SAM's formal type)
	//   var id          — Java 11 `var` lambda param (slice 26 will
	//                     gate this at JLS 11)
	// The user-supplied type is consumed and discarded — JCGO's lambda
	// synthesis uses the SAM parameter type unconditionally so the
	// signature matches the functional interface.
	private static void parseLambdaParam(ObjVector ids) {
		boolean isVar = t.kind == 1 && "var".equals(t.val)
			&& peek(2).kind == 1;
		if (isVar) {
			if (Main.dict.javaVersion < JavaVersion.JLS_110) {
				SemError("var in lambda parameter requires -source 11 or higher (got "
					+ JavaVersion.format(Main.dict.javaVersion) + ")");
			}
			Get();  // consume `var`
		} else if (isLambdaParamTyped()) {
			SimpleType();
			// SimpleType doesn't consume `[]` dims — accept them too.
			if (t.kind == 43) DimSpecSeq();
		}
		if (t.kind != 1) {
			SemError("expected lambda parameter identifier");
			return;
		}
		ids.addElement(new LexTerm(LexTerm.ID, t.val));
		Get();
	}

	private static boolean isLambdaParamTyped() {
		// Primitive type keyword is unambiguous.
		if (t.kind >= 35 && t.kind <= 42) return true;
		// Identifier could be either a type or the param name itself.
		// Disambiguate: if the next token is `,` or `)`, treat as bare
		// param name (untyped); otherwise it's a type followed by name.
		if (t.kind == 1) {
			int idx = 2;
			// Walk past dotted qualifier (`java.util.List`).
			while (peek(idx).kind == 13 && peek(idx + 1).kind == 1) {
				idx += 2;
			}
			// Skip `<...>` generic args.
			if (peek(idx).kind == 73) {
				int depth = 1;
				idx++;
				while (depth > 0) {
					int k = peek(idx).kind;
					if (k == 0) return false;
					if (k == 73) depth++;
					else if (k == 75) depth--;
					else if (k == 70) depth -= 2;
					else if (k == 69) depth -= 3;
					idx++;
					if (idx > 256) return false;
				}
			}
			// Skip `[]` array dims.
			while (peek(idx).kind == 43 && peek(idx + 1).kind == 44) {
				idx += 2;
			}
			int next = peek(idx).kind;
			return next == 1; // identifier follows → type+id form
		}
		return false;
	}

	private static Term FieldDeclTail(Term a, Term b, Term c) {
		Term z;
		Term d = Empty.term, f = Empty.term, h = null;
		
		if (t.kind == 43) {
			d = DimSpecSeq();
		}
		if (t.kind == 46) {
			Get();
			f = VariableInitializer();
		}
		if (t.kind == 27) {
			Get();
			h = VariableDeclaratorList();
		}
		Expect(9);
		z = new FieldDeclaration(a, b, h != null ?
		     (Term) (new VariableDeclareList(new VariableDeclarator(
		     new VariableIdentifier(c), d, f), h)) :
		     new VariableDeclarator(new VariableIdentifier(c), d, f));
		
		return z;
	}

	private static Term MethodDeclTail(Term a, Term b, Term c) {
		Term z;
		Term e = Empty.term, g = Empty.term, h = Empty.term, i;
		
		Expect(11);
		if (StartOf(21)) {
			e = FormalParamList();
		}
		Expect(12);
		if (t.kind == 43) {
			g = DimSpecSeq();
		}
		if (t.kind == 45) {
			h = ThrowsDeclaration();
		}
		i = SemiOrBlock();
		z = new MethodDeclaration(a, b, c, e, g, h, i);
		return z;
	}

	private static Term MethodDeclOrFieldDeclTail(Term a, Term b, Term c) {
		Term z;
		z = Empty.term;
		
		if (t.kind == 11) {
			z = MethodDeclTail(a, b, c);
		} else if (StartOf(22)) {
			z = FieldDeclTail(a, b, c);
		} else Error(142);
		return z;
	}

	private static Term MethodDeclOrFieldDeclBody(Term a) {
		Term z;
		Term a2 = Empty.term, b = Empty.term, c;

		if (t.kind == 13) {
			Get();
			a2 = QualifiedIdentifier();
		}
		// Slice 47: erase generic args on the return / field type —
		// `List<T>` or `Map.Entry<K, V>`. Slice 24 already handles
		// generic args inside SimpleType, but the field/method decl
		// head bypasses SimpleType so we need to consume them here.
		// Slice 50: capture the inner args (when supportable) so the
		// field/return type's JLS signature can include them.
		String capturedArgs = null;
		if (t.kind == 73) {
			capturedArgs = captureGenericArgsToJls();
		}
		if (t.kind == 43) {
			b = DimSpecSeq();
		}
		c = Identifier();
		// Slice 45: the type for an ID-prefixed field/method decl is
		// constructed manually here, so SimpleType's erasure hook is
		// bypassed. Apply it directly to the wrapped name.
		Term qname = new QualifiedName(a, a2);
		if (capturedArgs != null) {
			recordCapturedGenericArgs(qname, capturedArgs);
		}
		Term typeName = eraseTypeParamRef(qname);
		z = MethodDeclOrFieldDeclTail(new ClassOrIfaceType(typeName), b, c);
		return z;
	}

	private static Term ConstructorDeclBody(Term a) {
		Term z;
		Term c = Empty.term, e = Empty.term, g = Empty.term;
		
		Expect(11);
		if (StartOf(21)) {
			c = FormalParamList();
		}
		Expect(12);
		if (t.kind == 45) {
			e = ThrowsDeclaration();
		}
		Expect(28);
		if (StartOf(13)) {
			g = BlockStatementSeq();
		}
		Expect(29);
		z = new ConstrDeclaration(a, c, e, g);
		return z;
	}

	private static Term ConstrOrMethodOrFieldDeclBody(Term a) {
		Term z;
		z = Empty.term;
		if (t.kind == 11) {
			z = ConstructorDeclBody(a);
		} else if (StartOf(23) || t.kind == 73) {
			// Slice 47 (Java 7+): generic return / field type at the
			// method/field decl head — `List<T> asList(...)` or
			// `Map<K, V> m;`. Routed through MethodDeclOrFieldDeclBody
			// which now consumes `<...>` after the leading qualifier.
			z = MethodDeclOrFieldDeclBody(a);
		} else Error(143);
		return z;
	}

	private static Term PrimitiveType() {
		Term z;
		z = Empty.term;
		switch (t.kind) {
		case 35: {
			Get();
			z = new PrimitiveType(Type.BOOLEAN);
			break;
		}
		case 36: {
			Get();
			z = new PrimitiveType(Type.BYTE);
			break;
		}
		case 37: {
			Get();
			z = new PrimitiveType(Type.CHAR);
			break;
		}
		case 38: {
			Get();
			z = new PrimitiveType(Type.SHORT);
			break;
		}
		case 39: {
			Get();
			z = new PrimitiveType(Type.INT);
			break;
		}
		case 40: {
			Get();
			z = new PrimitiveType(Type.LONG);
			break;
		}
		case 41: {
			Get();
			z = new PrimitiveType(Type.FLOAT);
			break;
		}
		case 42: {
			Get();
			z = new PrimitiveType(Type.DOUBLE);
			break;
		}
		default: Error(144);
		}
		return z;
	}

	private static Term DimSpecSeq() {
		Term z;
		Term c = Empty.term;
		Expect(43);
		Expect(44);
		if (t.kind == 43) {
			c = DimSpecSeq();
		}
		z = new DimSpec(c);
		return z;
	}

	// Slice 44 (Java 8 / JSR 308): type-use annotations. Parse and
	// discard `@Anno`/`@Anno(args)` at the head of a type. Gated at
	// JLS_80 so older source levels still reject them. Slice 49: while
	// inside this group, Annotation() doesn't capture into the
	// declaration-annotation buffer — type-use annotations don't count
	// as declared on the surrounding declaration.
	private static void TypeUseAnnotationGroup() {
		if (Main.dict.javaVersion < JavaVersion.JLS_80) {
			SemError("type-use annotation requires -source 8 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		boolean prev = inTypeUseAnnotationContext;
		inTypeUseAnnotationContext = true;
		try {
			AnnotationGroup();
		} finally {
			inTypeUseAnnotationContext = prev;
		}
	}

	private static Term SimpleType() {
		Term z;
		z = Empty.term; Term a;
		// Slice 44 (Java 8 / JSR 308): type-use annotations. Parse and
		// discard `@Anno` (or `@Anno(args)`) at the head of a type.
		if (t.kind == 10) {
			TypeUseAnnotationGroup();
		}
		if (t.kind == 1 || t.kind == 7) {
			a = QualifiedIdentifier();
			// Slice 24 (Java 5): erase generic type arguments. Slice
			// 50 (inner generic-arg retention): capture the args as a
			// JLS-form string side-channeled to the leading name, so
			// codegen can resolve to ParameterizedType. Falls back to
			// consumeGenericArgs's discard for wildcards / nested /
			// arrays which captureGenericArgsToJls doesn't support.
			if (t.kind == 73) {
				String capturedArgs = captureGenericArgsToJls();
				if (capturedArgs != null) {
					recordCapturedGenericArgs(a, capturedArgs);
				}
			}
			// Slice 45: erase a single-id name that matches a
			// declared type-parameter to java.lang.Object.
			a = eraseTypeParamRef(a);
			z = new ClassOrIfaceType(a);
		} else if (StartOf(2)) {
			z = PrimitiveType();
		} else Error(145);
		return z;
	}

	// Slice 45: if `name` is a single-id QualifiedName matching a
	// declared type-parameter in any active scope, substitute its
	// erasure. Slice 46: `<T extends Number>` -> Number; unbounded ->
	// java.lang.Object. Slice 50 (pre-erasure retention): record the
	// original type-parameter name in a side channel keyed by the
	// substituted Term so codegen can rebuild a JLS signature with
	// `TT;` references instead of the erased `Ljava/lang/Object;`.
	private static Term eraseTypeParamRef(Term name) {
		if (!(name instanceof QualifiedName)) return name;
		String dotted = ((QualifiedName) name).dottedName();
		if (dotted == null || dotted.indexOf('.') >= 0) return name;
		if (!isActiveTypeParam(dotted)) return name;
		String bound = activeBoundFor(dotted);
		Term replacement = bound == null ? objectTypeName()
				: qualifiedNameFromDotted(bound);
		erasedTypeOriginalNames.put(replacement, dotted);
		return replacement;
	}

	// Slice 50 (pre-erasure retention): side channel from the Term
	// produced by eraseTypeParamRef back to the original
	// type-parameter name (e.g. "T"). Read by MethodDefinition's
	// signature builder to emit `TT;` in place of the erased
	// reference.
	private static final ObjHashtable erasedTypeOriginalNames =
			new ObjHashtable();

	static String getErasedTypeVarName(Term name) {
		return name == null ? null
				: (String) erasedTypeOriginalNames.get(name);
	}

	private static Term objectTypeName() {
		return qualifiedNameFromDotted("java.lang.Object");
	}

	// Build a QualifiedName tree from a dotted string, e.g.
	// "java.lang.Number" -> QN("java", QN("lang", QN("Number", Empty))).
	private static Term qualifiedNameFromDotted(String dotted) {
		Term tail = Empty.newTerm();
		int idx = dotted.length();
		while (idx > 0) {
			int prev = dotted.lastIndexOf('.', idx - 1);
			String part = dotted.substring(prev + 1, idx);
			tail = new QualifiedName(new LexTerm(LexTerm.ID, part), tail);
			idx = prev;
		}
		return tail;
	}

	// Slice 45: capture type-parameter NAMES from a `<T, U extends X>`
	// declaration list (NOT a type-arg list — those go through
	// consumeGenericArgs). Same token-walking shape as
	// consumeGenericArgs, but at depth 1 grabs the first identifier
	// of each comma-separated entry. Slice 46: also captures the
	// FIRST class name after `extends` (the JLS-erasure bound) so
	// `<T extends Number>` substitutes Number, not Object. Multi-bound
	// `& X` and the bound's own type-args (e.g. `Comparable<T>`) are
	// walked but not retained.
	//
	// Result is a flat ObjVector with paired entries: index 2*i is
	// the param name (String), 2*i+1 is the dotted bound name
	// (String) or null when unbounded.
	private static ObjVector consumeTypeParamList() {
		if (Main.dict.javaVersion < JavaVersion.JLS_50) {
			SemError("generic type parameters requires -source 5 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		ObjVector entries = new ObjVector();
		Expect(73);  // `<`
		int depth = 1;
		// State at depth 1:
		//   EXPECT_NAME  -> next ID is a param name
		//   AFTER_NAME   -> waiting for `,`/`>`/`extends`
		//   IN_BOUND     -> capturing dotted bound head
		//   AFTER_BOUND  -> bound captured, skipping `& X` etc.
		final int EXPECT_NAME = 0;
		final int AFTER_NAME = 1;
		final int IN_BOUND_HEAD = 2;
		final int AFTER_BOUND = 3;
		int state = EXPECT_NAME;
		StringBuffer boundBuf = null;
		boolean boundExpectDotIdent = false;
		while (depth > 0 && t.kind != 0) {
			if (depth == 1) {
				if (state == EXPECT_NAME && t.kind == 1) {
					entries.addElement(t.val);
					entries.addElement(null);
					state = AFTER_NAME;
					Get();
					continue;
				}
				if (state == AFTER_NAME && t.kind == 25) {
					// `extends` — start capturing bound head.
					state = IN_BOUND_HEAD;
					boundBuf = new StringBuffer();
					boundExpectDotIdent = false;
					Get();
					continue;
				}
				if (state == IN_BOUND_HEAD && !boundExpectDotIdent
						&& (t.kind == 1 || t.kind == 7)) {
					boundBuf.append(t.val);
					boundExpectDotIdent = true;
					Get();
					continue;
				}
				if (state == IN_BOUND_HEAD && boundExpectDotIdent
						&& t.kind == 13) {
					boundBuf.append('.');
					boundExpectDotIdent = false;
					Get();
					continue;
				}
				if (state == IN_BOUND_HEAD) {
					// Anything else closes the bound head — record it
					// and transition. Generic args / `& X` / `,` / `>`
					// are handled by the depth/comma logic below.
					if (boundBuf.length() > 0) {
						entries.setElementAt(boundBuf.toString(),
								entries.size() - 1);
					}
					state = AFTER_BOUND;
					boundBuf = null;
					// fall through to handle the current token
				}
			}
			if (t.kind == 73) {
				Get();
				depth++;
			} else if (t.kind == 75) {
				Get();
				depth--;
			} else if (t.kind == 70) {
				if (depth < 2) break;
				Get();
				depth -= 2;
			} else if (t.kind == 69) {
				if (depth < 3) break;
				Get();
				depth -= 3;
			} else if (t.kind == 27 && depth == 1) {
				state = EXPECT_NAME;
				Get();
			} else {
				Get();
			}
		}
		return entries;
	}

	private static void pushTypeParamScope(ObjVector names) {
		typeParamScopes.addElement(names != null ? names : new ObjVector());
	}

	private static void popTypeParamScope() {
		if (typeParamScopes.size() > 0) {
			typeParamScopes.removeElementAt(typeParamScopes.size() - 1);
		}
	}

	private static boolean isActiveTypeParam(String name) {
		for (int i = typeParamScopes.size() - 1; i >= 0; i--) {
			ObjVector scope = (ObjVector) typeParamScopes.elementAt(i);
			// Slice 46: scope is [name, bound, name, bound, ...].
			for (int j = 0; j < scope.size(); j += 2) {
				if (name.equals(scope.elementAt(j))) return true;
			}
		}
		return false;
	}

	// Slice 46: dotted bound name for an active type-param, or null
	// if unbounded / not in scope. Innermost scope wins on shadowing.
	private static String activeBoundFor(String name) {
		for (int i = typeParamScopes.size() - 1; i >= 0; i--) {
			ObjVector scope = (ObjVector) typeParamScopes.elementAt(i);
			for (int j = 0; j < scope.size(); j += 2) {
				if (name.equals(scope.elementAt(j))) {
					Object b = scope.elementAt(j + 1);
					return b == null ? null : (String) b;
				}
			}
		}
		return null;
	}

	// Slice 50 (inner generic-arg retention): side channel from a
	// type Term (the QualifiedName produced by the type's leading
	// identifier) to the JLS-form generic-args string captured at
	// parse time, e.g. `<TT;>` for `List<T>`. Read at codegen time
	// when building method/field signatures so getGenericReturnType
	// / getGenericType can resolve to ParameterizedType.
	private static final ObjHashtable capturedGenericArgs =
			new ObjHashtable();

	static String getCapturedGenericArgs(Term name) {
		return name == null ? null
				: (String) capturedGenericArgs.get(name);
	}

	// Capture-or-discard recursive-descent variant of
	// consumeGenericArgs. Called by type-use sites that want
	// pre-erasure retention. Consumes the `<...>` block and returns
	// the JLS-form `<arg1arg2...>` string. Supports type-var
	// references, qualified class names, nested generics
	// (`Map<String, List<T>>`), wildcards (`<? extends X>`,
	// `<? super Y>`, `<?>`), array type args (`List<int[]>`,
	// `List<String[]>`), and primitive type args. Returns null when
	// the args contain something genuinely unsupported.
	//
	// The `>>` and `>>>` close-angle tokens are split via a static
	// pendingCloseAngles counter — when one is consumed at depth
	// boundary K it leaves K-1 pending, which the next outer-level
	// close consumes without advancing the token cursor.
	private static int pendingCloseAngles;

	private static String captureGenericArgsToJls() {
		if (t.kind != 73) return null;
		Get();  // <
		StringBuffer sb = new StringBuffer();
		sb.append('<');
		while (!atClosingAngle()) {
			if (t.kind == 27) {
				Get();
				continue;
			}
			String arg = captureOneTypeArg();
			if (arg == null) return null;
			sb.append(arg);
		}
		consumeOneClosingAngle();
		sb.append('>');
		return sb.toString();
	}

	private static boolean atClosingAngle() {
		return pendingCloseAngles > 0
			|| t.kind == 75 || t.kind == 70 || t.kind == 69
			|| t.kind == 0;
	}

	private static void consumeOneClosingAngle() {
		if (pendingCloseAngles > 0) {
			pendingCloseAngles--;
			return;
		}
		if (t.kind == 75) {
			Get();
		} else if (t.kind == 70) {
			Get();
			pendingCloseAngles = 1;
		} else if (t.kind == 69) {
			Get();
			pendingCloseAngles = 2;
		}
	}

	private static String captureOneTypeArg() {
		// Slice 44 type-use annotations may decorate the type arg
		// itself (`<@NonNull T>`); skip them.
		if (t.kind == 10) {
			TypeUseAnnotationGroup();
		}
		// Wildcards: ?, ? extends X, ? super Y.
		if (t.kind == 58) {
			Get();
			if (t.kind == 10) {
				TypeUseAnnotationGroup();
			}
			if (t.kind == 25) {  // extends
				Get();
				String type = captureFieldTypeSig();
				if (type == null) return null;
				return "+" + type;
			}
			if (t.kind == 101) {  // super
				Get();
				String type = captureFieldTypeSig();
				if (type == null) return null;
				return "-" + type;
			}
			return "*";
		}
		return captureFieldTypeSig();
	}

	private static String captureFieldTypeSig() {
		// Primitive: kinds 35..42 (boolean/byte/char/short/int/
		// long/float/double).
		String prim = primitiveJlsCode(t.kind);
		if (prim != null) {
			Get();
			int dims = consumeDimSuffix();
			StringBuffer arr = new StringBuffer();
			for (int i = 0; i < dims; i++) arr.append('[');
			arr.append(prim);
			return arr.toString();
		}
		// Reference type: ID, possibly qualified.
		if (t.kind != 1 && t.kind != 7) return null;
		StringBuffer name = new StringBuffer(t.val);
		Get();
		while (t.kind == 13 && peek(2).kind == 1) {
			Get();
			name.append('.').append(t.val);
			Get();
		}
		String dotted = name.toString();
		StringBuffer sb = new StringBuffer();
		if (dotted.indexOf('.') < 0 && isActiveTypeParam(dotted)) {
			sb.append('T').append(dotted).append(';');
		} else {
			sb.append('L').append(dotted.replace('.', '/'));
			if (t.kind == 73) {
				String inner = captureGenericArgsToJls();
				if (inner == null) return null;
				sb.append(inner);
			}
			sb.append(';');
		}
		int dims = consumeDimSuffix();
		if (dims > 0) {
			StringBuffer arr = new StringBuffer();
			for (int i = 0; i < dims; i++) arr.append('[');
			arr.append(sb);
			return arr.toString();
		}
		return sb.toString();
	}

	private static int consumeDimSuffix() {
		int dims = 0;
		while (t.kind == 43 && peek(2).kind == 44) {
			Get();
			Get();
			dims++;
		}
		return dims;
	}

	private static String primitiveJlsCode(int kind) {
		switch (kind) {
			case 35: return "Z";  // boolean
			case 36: return "B";  // byte
			case 37: return "C";  // char
			case 38: return "S";  // short
			case 39: return "I";  // int
			case 40: return "J";  // long
			case 41: return "F";  // float
			case 42: return "D";  // double
		}
		return null;
	}

	private static void recordCapturedGenericArgs(Term name, String jls) {
		if (name != null && jls != null) {
			capturedGenericArgs.put(name, jls);
		}
	}

	// Slice 24: balanced consumer for `<...>` type-argument blocks.
	// The scanner tokenizes `>>` and `>>>` as single SHIFT tokens —
	// when one closes multiple generic-arg layers the consumer
	// decrements depth by 2 / 3 accordingly.
	private static void consumeGenericArgs() {
		if (Main.dict.javaVersion < JavaVersion.JLS_50) {
			SemError("generic type arguments requires -source 5 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Expect(73);  // `<`
		int depth = 1;
		while (depth > 0 && t.kind != 0) {
			if (t.kind == 73) {
				Get();
				depth++;
			} else if (t.kind == 75) {
				Get();
				depth--;
			} else if (t.kind == 70) {
				// `>>` closes two angle layers in one token.
				if (depth < 2) break;
				Get();
				depth -= 2;
			} else if (t.kind == 69) {
				// `>>>` closes three layers.
				if (depth < 3) break;
				Get();
				depth -= 3;
			} else {
				Get();
			}
		}
	}

	private static Term FinalModifier() {
		Term z;
		Expect(20);
		z = new AccModifier(AccModifier.FINAL);
		return z;
	}

	private static Term FormalParam() {
		Term z;
		Term a = Empty.term, b, c = Empty.term, d, e = Empty.term;
		boolean isVarArgs = false;

		if (t.kind == 20) {
			a = FinalModifier();
		}
		// Slice 49 ext: snapshot the pending-annotation list before
		// parsing any parameter-level annotations so they get
		// attributed to this FormalParameter (via recordParamAnnotations
		// below) instead of bubbling up to the surrounding method.
		int paramAnnoSnap = snapshotPendingAnnotations();
		if (t.kind == 10) {
			AnnotationGroup();
		}
		ObjVector paramAnnos = takePendingAnnotationsSince(paramAnnoSnap);
		b = SimpleType();
		if (t.kind == 43) {
			c = DimSpecSeq();
		}
		// Varargs: `T...` = `T[]`. Synthesize one extra dim. Detection is
		// 3-token lookahead via peek(); QualifiedIdentifier above stops at
		// `..` so the first `.` of an ellipsis doesn't get consumed there.
		if (t.kind == 13 && peek(2).kind == 13 && peek(3).kind == 13) {
			Get(); Get(); Get();
			isVarArgs = true;
			c = (c == Empty.term)
				? (Term) new DimSpec(Empty.newTerm())
				: (Term) new DimSpec(c);
		}
		d = Identifier();
		if (t.kind == 43) {
			e = DimSpecSeq();
		}
		FormalParameter fp = new FormalParameter(a, b, c,
				new VariableIdentifier(d), e);
		if (isVarArgs) fp.setVarArgs();
		// Slice 49 ext: stash the parameter-level annotations on a
		// side channel keyed by this FormalParameter, so codegen can
		// emit them per-parameter for Method/Constructor reflection.
		if (paramAnnos != null) {
			recordParamAnnotations(fp, paramAnnos);
		}
		z = fp;

		return z;
	}

	private static Term SemiOrBlock() {
		Term z;
		z = Empty.term;
		if (t.kind == 9) {
			Get();
			z = new Block();
		} else if (t.kind == 28) {
			z = JavaBlock();
		} else Error(146);
		return z;
	}

	private static Term ThrowsDeclaration() {
		Term z;
		Expect(45);
		z = ClassTypeList();
		return z;
	}

	private static Term FormalParamList() {
		Term z;
		Term a, c = null;
		a = FormalParam();
		if (t.kind == 27) {
			Get();
			c = FormalParamList();
		}
		z = c != null ? new FormalParamList(a, c) : a;
		
		return z;
	}

	private static Term ExtendsInterfaceTypes() {
		Term z;
		Expect(25);
		z = ClassTypeList();
		return z;
	}

	private static Term BlockStatement() {
		Term z;
		z = Empty.term;
		// Slice 44: leading type-use annotation on a local var decl
		// (`@Anno T x = ...;`) at block level.
		if (t.kind == 10) {
			AnnotationGroup();
		}
		if (t.kind == 20) {
			Get();
			z = FinalClsDeclOrVarDeclStmtTail();
		} else if (StartOf(24)) {
			z = ModifiersLocClassDeclNoFinal();
		} else if (StartOf(25)) {
			z = JavaStatement();
		} else Error(147);
		return z;
	}

	private static Term BlockStatementSeq() {
		Term z;
		Term a, b = null;
		a = BlockStatement();
		if (StartOf(13) || t.kind == 10) {
			b = BlockStatementSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term PrimitiveMethodFieldDecl() {
		Term z;
		Term a, b = Empty.term, c;
		a = PrimitiveType();
		if (t.kind == 43) {
			b = DimSpecSeq();
		}
		c = Identifier();
		z = MethodDeclOrFieldDeclTail(a, b, c);
		return z;
	}

	private static Term ConstrMethodFieldDecl() {
		Term z;
		Term a;
		a = Identifier();
		z = ConstrOrMethodOrFieldDeclBody(a);
		return z;
	}

	private static Term VoidMethodDecl() {
		Term z;
		Term b, d = Empty.term, f = Empty.term, g;
		b = Identifier();
		Expect(11);
		if (StartOf(21)) {
			d = FormalParamList();
		}
		Expect(12);
		if (t.kind == 45) {
			f = ThrowsDeclaration();
		}
		g = SemiOrBlock();
		z = new MethodDeclaration(new PrimitiveType(Type.VOID), b, d, f, g);
		
		return z;
	}

	private static Term JavaBlock() {
		Term z;
		Term b = Empty.term;
		Expect(28);
		if (StartOf(13) || t.kind == 10) {
			b = BlockStatementSeq();
		}
		Expect(29);
		z = new Block(b);
		return z;
	}

	private static Term AccModifier() {
		Term z;
		z = Empty.term;
		// Slice 52: sealed and non-sealed propagate through the
		// modifier pipeline as real AccModifier bits.
		if (looksLikeNonSealed()) {
			consumeNonSealed();
			return new AccModifier(AccModifier.NON_SEALED);
		}
		if (looksLikeSealed()) {
			if (Main.dict.javaVersion < JavaVersion.JLS_170) {
				SemError("sealed requires -source 17 or higher (got "
					+ JavaVersion.format(Main.dict.javaVersion) + ")");
			}
			Get();
			return new AccModifier(AccModifier.SEALED);
		}
		switch (t.kind) {
		case 16: {
			Get();
			z = new AccModifier(AccModifier.PUBLIC);
			break;
		}
		case 17: {
			Get();
			z = new AccModifier(AccModifier.PRIVATE);
			break;
		}
		case 18: {
			Get();
			z = new AccModifier(AccModifier.PROTECTED);
			break;
		}
		case 19: {
			Get();
			z = new AccModifier(AccModifier.STATIC);
			break;
		}
		case 20: {
			Get();
			z = new AccModifier(AccModifier.FINAL);
			break;
		}
		case 30: {
			Get();
			z = new AccModifier(AccModifier.SYNCHRONIZED);
			break;
		}
		case 31: {
			Get();
			z = new AccModifier(AccModifier.VOLATILE);
			break;
		}
		case 32: {
			Get();
			z = new AccModifier(AccModifier.TRANSIENT);
			break;
		}
		case 33: {
			Get();
			z = new AccModifier(AccModifier.NATIVE);
			break;
		}
		case 21: {
			Get();
			z = new AccModifier(AccModifier.ABSTRACT);
			break;
		}
		case 22: {
			Get();
			z = new AccModifier(AccModifier.STRICT);
			break;
		}
		case 60: {
			Get();
			z = new AccModifier(AccModifier.DEFAULT);
			break;
		}
		case 10: {
			Annotation();
			break;
		}
		default: Error(148);
		}
		return z;
	}

	private static Term MemberDecl() {
		Term z;
		z = Empty.term; Term a;
		// Slice 24b: `<T> ReturnType foo(T x) ...` — generic-method
		// type-parameter prefix. Slice 45: capture the names so they
		// erase to Object inside the return type, parameter types,
		// throws clause, and body. Slice 45b: keep the list so codegen
		// can recover the original signature on the resulting method
		// (or constructor) AST node.
		if (t.kind == 73) {
			ObjVector captured = consumeTypeParamList();
			pushTypeParamScope(captured);
			try {
				Term result = MemberDecl();
				recordGenericSignature(result, captured);
				return result;
			} finally {
				popTypeParamScope();
			}
		}
		switch (t.kind) {
		case 28: {
			a = JavaBlock();
			z = new StaticInitializer(a);
			break;
		}
		case 23: {
			Get();
			z = ClassDeclaration();
			break;
		}
		case 24: {
			Get();
			z = InterfaceDeclaration();
			break;
		}
		case 34: {
			Get();
			z = VoidMethodDecl();
			break;
		}
		case 1: case 7: {
			if (looksLikeRecord()) {
				Get();
				z = RecordDeclaration();
			} else if (looksLikeEnum()) {
				Get();
				z = EnumDeclaration();
			} else if (looksLikeCompactCtor()) {
				z = CompactCanonicalCtor();
			} else {
				z = ConstrMethodFieldDecl();
			}
			break;
		}
		case 35: case 36: case 37: case 38: case 39: case 40: case 41: case 42: {
			z = PrimitiveMethodFieldDecl();
			break;
		}
		default: Error(149);
		}
		return z;
	}

	private static Term ModifierSeq() {
		Term z;
		Term a, b = null;
		a = AccModifier();
		if (StartOf(26) || t.kind == 60 || looksLikeSealedKw()) {
			b = ModifierSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term ClassBodyDecl() {
		Term z;
		Term a = Empty.term, b;
		// Slice 49: snapshot the pending-annotation list before parsing
		// this member's modifiers so the take afterwards picks up only
		// THIS member's annotations and doesn't steal anything that
		// belongs to the enclosing class declaration.
		int annoSnap = snapshotPendingAnnotations();
		if (StartOf(26) || t.kind == 60 || looksLikeSealedKw()) {
			a = ModifierSeq();
		}
		b = MemberDecl();
		recordAnnotations(b, takePendingAnnotationsSince(annoSnap));
		z = new TypeDeclaration(a, b);
		return z;
	}

	private static Term SemiOrClassBodyDecl() {
		Term z;
		z = Empty.term;
		if (t.kind == 9) {
			Get();
		} else if (StartOf(27) || t.kind == 60 || looksLikeSealedKw()
				|| looksLikeRecord()) {
			z = ClassBodyDecl();
		} else Error(150);
		return z;
	}

	private static Term SemiOrClassBodyDeclSeq() {
		Term z;
		Term a, b = null;
		a = SemiOrClassBodyDecl();
		if (StartOf(28) || t.kind == 60 || looksLikeSealedKw()
				|| looksLikeRecord()) {
			b = SemiOrClassBodyDeclSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term ClassTypeList() {
		Term z;
		Term a, c = null;
		// Slice 44: type-use annotations on throws clause types.
		if (t.kind == 10) {
			TypeUseAnnotationGroup();
		}
		a = QualifiedIdentifier();
		// Slice 51: accept generic args on each type in an
		// `implements`/`extends interfaces`/`throws`/`permits` list,
		// e.g. `implements Comparable<String>`. Erased per slice 24.
		if (t.kind == 73) {
			consumeGenericArgs();
		}
		a = eraseTypeParamRef(a);
		if (t.kind == 27) {
			Get();
			c = ClassTypeList();
		}
		z = c != null ? (Term) (new Seq(new ClassOrIfaceType(a), c)) :
		     new ClassOrIfaceType(a);

		return z;
	}

	private static Term ClassBody() {
		Term z;
		Term b = Empty.term;
		Expect(28);
		if (StartOf(28) || t.kind == 60) {
			b = SemiOrClassBodyDeclSeq();
		}
		Expect(29);
		z = new Seq(b, Empty.term);
		return z;
	}

	private static Term ImplementsTypes() {
		Term z;
		Expect(26);
		z = ClassTypeList();
		return z;
	}

	private static Term ExtendsType() {
		Term z;
		Term b;
		Expect(25);
		// Slice 51: accept type-use annotations + generic args on the
		// supertype — `class Foo extends Bar<String>` etc. Slice 24's
		// erasure walks the `<...>` and discards. The presence of
		// generic args is recorded so bridge-method synthesis (also
		// slice 51) can identify covariant-override candidates.
		if (t.kind == 10) {
			TypeUseAnnotationGroup();
		}
		b = QualifiedIdentifier();
		if (t.kind == 73) {
			consumeGenericArgs();
			lastExtendsHadTypeArgs = true;
		}
		b = eraseTypeParamRef(b);
		z = new ClassOrIfaceType(b);
		return z;
	}

	// Slice 51: set true by ExtendsType when the supertype had a
	// `<TypeArgs>` suffix. Captured by ClassDeclaration immediately
	// after the extends parse, then cleared.
	private static boolean lastExtendsHadTypeArgs;

	private static Term InterfaceDeclaration() {
		Term z;
		Term b, c = Empty.term, d;
		b = Identifier();
		// Slice 24: `interface Foo<T, U> ...` — consume the
		// type-parameter list. Slice 45: capture the names so they
		// erase to Object inside the body. Slice 45b: keep the list
		// so codegen can recover the original signature.
		boolean pushed = false;
		ObjVector captured = null;
		if (t.kind == 73) {
			captured = consumeTypeParamList();
			pushTypeParamScope(captured);
			pushed = true;
		}
		ObjVector permits = null;
		try {
			if (t.kind == 25) {
				c = ExtendsInterfaceTypes();
			}
			if (looksLikePermits()) {
				permits = consumePermitsClause();
			}
			d = ClassBody();
		} finally {
			if (pushed) popTypeParamScope();
		}
		z = new IfaceDeclaration(b, c, d);
		recordGenericSignature(z, captured);
		recordPermitsList(z, permits);
		return z;
	}

	private static Term ClassDeclaration() {
		Term z;
		Term b, c = Empty.term, d = Empty.term, e;
		b = Identifier();
		// Slice 24: `class Foo<T, U extends Number> ...` — consume the
		// type-parameter list. Slice 45: capture the names so they
		// erase to Object inside the body. Slice 45b: keep the same
		// list so codegen can later recover the original signature.
		boolean pushed = false;
		ObjVector captured = null;
		if (t.kind == 73) {
			captured = consumeTypeParamList();
			pushTypeParamScope(captured);
			pushed = true;
		}
		ObjVector permits = null;
		boolean extendsParameterized = false;
		try {
			lastExtendsHadTypeArgs = false;
			if (t.kind == 25) {
				c = ExtendsType();
				extendsParameterized = lastExtendsHadTypeArgs;
			}
			if (t.kind == 26) {
				d = ImplementsTypes();
			}
			if (looksLikePermits()) {
				permits = consumePermitsClause();
			}
			e = ClassBody();
		} finally {
			if (pushed) popTypeParamScope();
		}
		// Slice 51: when extending a parameterized supertype, walk
		// the body and synthesize bridge methods for each declared
		// method whose param list contains a non-Object reference
		// type. Without these the analyzer doesn't recognize the
		// covariant override and the parent's method gets
		// devirtualized away.
		if (extendsParameterized && e.notEmpty()) {
			e = BridgeSynthesis.wrap(e);
		}
		z = new ClassDeclaration(b, c, d, e);
		recordGenericSignature(z, captured);
		recordPermitsList(z, permits);
		return z;
	}

	// Sealed types (Java 17). `sealed`, `non-sealed`, and `permits` are
	// contextual keywords (identifier kind), recognized only in class /
	// interface declaration position so they remain usable as ordinary
	// identifiers elsewhere. `non-sealed` is hyphenated, so the lookahead
	// peeks across the three tokens `non` `-` `sealed`; the modifier
	// position guards mean an expression like `non - sealed` parses as
	// arithmetic when those names are local variables.
	private static boolean looksLikeSealed() {
		return t.kind == 1 && "sealed".equals(t.val);
	}

	private static boolean looksLikeNonSealed() {
		return t.kind == 1 && "non".equals(t.val)
			&& peek(2).kind == 67
			&& peek(3).kind == 1 && "sealed".equals(peek(3).val);
	}

	private static boolean looksLikeSealedKw() {
		return looksLikeSealed() || looksLikeNonSealed();
	}

	private static void consumeNonSealed() {
		if (Main.dict.javaVersion < JavaVersion.JLS_170) {
			SemError("non-sealed requires -source 17 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Get(); Get(); Get();
	}

	private static boolean looksLikePermits() {
		return t.kind == 1 && "permits".equals(t.val);
	}

	// Slice 52: capture the dotted class names listed in
	// `permits A, B, C`. Returns an ObjVector of String. Annotations
	// preceding each name (JSR 308 type-use) are walked but ignored.
	private static ObjVector consumePermitsClause() {
		if (Main.dict.javaVersion < JavaVersion.JLS_170) {
			SemError("sealed/permits requires -source 17 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Get();  // `permits`
		ObjVector names = new ObjVector();
		while (true) {
			if (t.kind == 10) AnnotationGroup();
			Term qn = QualifiedIdentifier();
			if (qn instanceof QualifiedName) {
				String dotted = ((QualifiedName) qn).dottedName();
				if (dotted != null) names.addElement(dotted);
			}
			if (t.kind == 27) {
				Get();
			} else {
				break;
			}
		}
		return names;
	}

	private static Term ClassModifier() {
		Term z;
		z = Empty.term;
		// Slice 52: sealed and non-sealed propagate through the
		// modifier pipeline as real AccModifier bits, so
		// ClassDefinition.modifiers reflects them and enforcement can
		// run later.
		if (looksLikeNonSealed()) {
			consumeNonSealed();
			return new AccModifier(AccModifier.NON_SEALED);
		}
		if (looksLikeSealed()) {
			if (Main.dict.javaVersion < JavaVersion.JLS_170) {
				SemError("sealed requires -source 17 or higher (got "
					+ JavaVersion.format(Main.dict.javaVersion) + ")");
			}
			Get();
			return new AccModifier(AccModifier.SEALED);
		}
		switch (t.kind) {
		case 16: {
			Get();
			z = new AccModifier(AccModifier.PUBLIC);
			break;
		}
		case 17: {
			Get();
			z = new AccModifier(AccModifier.PRIVATE);
			break;
		}
		case 18: {
			Get();
			z = new AccModifier(AccModifier.PROTECTED);
			break;
		}
		case 19: {
			Get();
			z = new AccModifier(AccModifier.STATIC);
			break;
		}
		case 20: {
			Get();
			z = new AccModifier(AccModifier.FINAL);
			break;
		}
		case 21: {
			Get();
			z = new AccModifier(AccModifier.ABSTRACT);
			break;
		}
		case 22: {
			Get();
			z = new AccModifier(AccModifier.STRICT);
			break;
		}
		case 10: {
			Annotation();
			break;
		}
		default: Error(151);
		}
		return z;
	}

	private static Term ClassDeclOrInterfaceDecl() {
		Term z;
		z = Empty.term;
		if (t.kind == 23) {
			Get();
			z = ClassDeclaration();
		} else if (t.kind == 24) {
			Get();
			z = InterfaceDeclaration();
		} else if (t.kind == 10 && peek(2).kind == 24) {
			Get(); Get();
			z = AnnotationTypeDeclaration();
		} else if (looksLikeRecord()) {
			Get();
			z = RecordDeclaration();
		} else if (looksLikeEnum()) {
			Get();
			z = EnumDeclaration();
		} else Error(152);
		return z;
	}

	// Records (Java 16): "record Identifier (".
	private static boolean looksLikeRecord() {
		return t.kind == 1 && "record".equals(t.val)
			&& peek(2).kind == 1 && peek(3).kind == 11;
	}

	// Enums (Java 5): "enum Identifier { " or "enum Identifier implements".
	// `enum` is a contextual keyword in this fork — Scanner still emits it
	// as kind 1 (identifier), so detection is by val.
	private static boolean looksLikeEnum() {
		if (t.kind != 1 || !"enum".equals(t.val)) return false;
		if (peek(2).kind != 1) return false;
		int k3 = peek(3).kind;
		return k3 == 28 || k3 == 26;  // `{` or `implements`
	}

	private static Term EnumDeclaration() {
		if (Main.dict.javaVersion < JavaVersion.JLS_50) {
			SemError("enum declaration requires -source 5 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Term name = Identifier();
		Term implementsList = Empty.newTerm();
		if (t.kind == 26) {
			implementsList = ImplementsTypes();
		}
		Expect(28);
		ObjVector constants = new ObjVector();
		// Parse comma-separated constants. Slice 19b: each may carry a
		// (args) list to be forwarded to the user-supplied constructor.
		// Anonymous bodies on constants are still deferred.
		if (t.kind == 1) {
			constants.addElement(parseEnumConstant());
			while (t.kind == 27) {
				Get();
				if (t.kind == 9 || t.kind == 29) break; // trailing comma
				if (t.kind != 1) {
					SemError("enum constant identifier expected");
					break;
				}
				constants.addElement(parseEnumConstant());
			}
		}
		// Optional `;` followed by class body members.
		Term userBody = Empty.newTerm();
		if (t.kind == 9) {
			Get();
			if (t.kind != 29) {
				userBody = SemiOrClassBodyDeclSeq();
			}
		}
		Expect(29);

		String enumName = name.dottedName();
		Term body = EnumSynthesis.buildBody(enumName, constants, userBody);
		Term extendsTerm = new ClassOrIfaceType(qualifiedNameTermFor(
				Names.JAVA_LANG_ENUM));
		Term classDecl = new ClassDeclaration(name, extendsTerm,
			implementsList, body);
		// Slice 19c: an enum with at least one anonymous-body constant
		// is implicitly NOT final — the constants are runtime
		// subclasses (JLS 8.9). Otherwise the default is final.
		// JCGO synthesizes STATIC because nested enums need it
		// (definePass0 only auto-adds STATIC for non-interface nested-
		// in-interface cases). The ENUM bit signals to processPass0
		// that the STATIC came from synthesis, so the "static at
		// top-level" rejection doesn't fire.
		AccModifier enumBit = new AccModifier(AccModifier.ENUM);
		AccModifier staticBit = new AccModifier(AccModifier.STATIC);
		Term modifiers = EnumSynthesis.anyConstantHasBody(constants)
			? (Term) new Seq(enumBit, staticBit)
			: (Term) new Seq(enumBit,
				new Seq(staticBit, new AccModifier(AccModifier.FINAL)));
		return new TypeDeclaration(modifiers, classDecl);
	}

	private static EnumSynthesis.EnumConstant parseEnumConstant() {
		String constName = t.val;
		Get();
		Term args = Empty.term;
		if (t.kind == 11) {
			Get();
			if (canStartArg()) {
				args = ArgumentList();
			}
			Expect(12);
		}
		Term classBody = Empty.term;
		// Slice 19c: optional `{ ... }` after the constant — anonymous
		// subclass body that overrides enum methods.
		if (t.kind == 28) {
			classBody = ClassBody();
		}
		return new EnumSynthesis.EnumConstant(constName, args, classBody);
	}

	private static Term qualifiedNameTermFor(String dotted) {
		Term qn = null;
		int idx = dotted.length();
		while (idx > 0) {
			int prev = dotted.lastIndexOf('.', idx - 1);
			String part = dotted.substring(prev + 1, idx);
			Term lt = new LexTerm(LexTerm.ID, part);
			qn = qn == null ? new QualifiedName(lt, Empty.newTerm())
				: new QualifiedName(lt, qn);
			idx = prev;
		}
		return qn;
	}

	// Slice 40: thread record name + header params into the body
	// parser so MemberDecl can recognize the no-paren compact
	// canonical constructor form `RecordName { body }`.
	static String currentRecordName;
	static Term currentRecordParams;

	private static boolean looksLikeCompactCtor() {
		if (currentRecordName == null) return false;
		if (t.kind != 1) return false;
		if (!currentRecordName.equals(t.val)) return false;
		return peek(2).kind == 28; // `{` immediately after the name
	}

	// Builds a regular ConstrDeclaration from the compact form.
	// `RecordName { user-body }` becomes
	//   `RecordName(<header params>) { user-body; this.x = x; ... }`
	// — the implicit field assignments come after the user body.
	private static Term CompactCanonicalCtor() {
		Term name = Identifier();
		Term userBlock = JavaBlock();
		// userBlock is `Block(LeftBrace, Seq(...), RightBrace)`.
		// Extract the inner statement seq.
		Term userStmts = Empty.newTerm();
		if (userBlock instanceof Block) {
			Block b = (Block) userBlock;
			if (b.terms.length >= 2) userStmts = b.terms[1];
		}
		Term assigns = buildRecordFieldAssignments(currentRecordParams);
		Term body = userStmts.notEmpty()
			? (assigns.notEmpty() ? new Seq(userStmts, assigns) : userStmts)
			: assigns;
		// Copy params for the synthesized ctor — parser owns the Term
		// tree, so a fresh copy avoids aliasing if anything mutates.
		Term paramsCopy = currentRecordParams;
		// Return the bare ConstrDeclaration — ClassBodyDecl wraps it
		// in a TypeDeclaration with whatever modifiers (`public`,
		// nothing, etc.) the user wrote in front of the compact form.
		return new ConstrDeclaration(name, paramsCopy,
			Empty.newTerm(), body);
	}

	private static Term buildRecordFieldAssignments(Term paramList) {
		ObjVector params = new ObjVector();
		flattenFormalParamsForCompactCtor(paramList, params);
		ObjVector stmts = new ObjVector();
		for (int i = 0; i < params.size(); i++) {
			FormalParameter fp = (FormalParameter) params.elementAt(i);
			String fieldName = fp.terms[3].dottedName();
			Term thisField = new PrimaryFieldAccess(new This(),
				new QualifiedName(new LexTerm(LexTerm.ID, fieldName),
					Empty.newTerm()));
			Term assign = new Assignment(thisField,
				new LexTerm(LexTerm.EQUALS, "="),
				new QualifiedName(new LexTerm(LexTerm.ID, fieldName),
					Empty.newTerm()));
			stmts.addElement(new ExprStatement(assign));
		}
		Term result = Empty.newTerm();
		for (int i = stmts.size() - 1; i >= 0; i--) {
			Term cur = (Term) stmts.elementAt(i);
			result = result.notEmpty() ? new Seq(cur, result) : cur;
		}
		return result;
	}

	private static void flattenFormalParamsForCompactCtor(Term t,
			ObjVector out) {
		if (!t.notEmpty()) return;
		if (t instanceof FormalParamList) {
			FormalParamList fpl = (FormalParamList) t;
			flattenFormalParamsForCompactCtor(fpl.terms[0], out);
			flattenFormalParamsForCompactCtor(fpl.terms[1], out);
		} else if (t instanceof FormalParameter) {
			out.addElement(t);
		}
	}

	private static Term RecordDeclaration() {
		if (Main.dict.javaVersion < JavaVersion.JLS_160) {
			SemError("record declaration requires -source 16 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Term name = Identifier();
		// Slice 45: optional `<T, U>` type-parameter list on the
		// record header. Captured so type-param refs in the component
		// list and body erase to Object. Slice 45b: keep the list so
		// codegen can recover the original signature on the inner
		// ClassDeclaration the record desugars to.
		boolean pushed = false;
		ObjVector capturedRecordTParams = null;
		if (t.kind == 73) {
			capturedRecordTParams = consumeTypeParamList();
			pushTypeParamScope(capturedRecordTParams);
			pushed = true;
		}
		Expect(11);
		Term params = Empty.newTerm();
		if (StartOf(21)) {
			params = FormalParamList();
		}
		Expect(12);
		Term implementsList = Empty.newTerm();
		if (t.kind == 26) {
			implementsList = ImplementsTypes();
		}
		Expect(28);
		// Slice 29: parse optional body members. A user-declared
		// canonical ctor (ConstrDeclaration with matching arity) is
		// detected by RecordSynthesis and replaces the synthesized
		// default. Other members pass through verbatim.
		// Slice 40: thread the record name + params so MemberDecl can
		// recognize the compact ctor form.
		String savedRecordName = currentRecordName;
		Term savedRecordParams = currentRecordParams;
		currentRecordName = name.dottedName();
		currentRecordParams = params;
		Term userBody = Empty.newTerm();
		if (t.kind != 29) {
			userBody = SemiOrClassBodyDeclSeq();
		}
		currentRecordName = savedRecordName;
		currentRecordParams = savedRecordParams;
		Expect(29);
		if (pushed) popTypeParamScope();
		Term body = RecordSynthesis.buildBody(name.dottedName(), params,
			userBody);
		Term classDecl = new ClassDeclaration(name, Empty.newTerm(),
			implementsList, body);
		recordGenericSignature(classDecl, capturedRecordTParams);
		// Records are implicitly final and (when nested) implicitly static.
		// Wrap in TypeDeclaration so the modifiers attach.
		Term modifiers = new Seq(new AccModifier(AccModifier.STATIC),
			new AccModifier(AccModifier.FINAL));
		return new TypeDeclaration(modifiers, classDecl);
	}

	// Build a synthetic interface that extends java.lang.annotation.Annotation,
	// exposing each annotation element as an abstract method. This makes the
	// annotation type a real ClassDefinition that runtime reflection
	// (Class.forName, Proxy.newProxyInstance) can resolve. `default V` clauses
	// have V captured as raw arg-text and side-channeled to the synthesized
	// MethodDeclaration so codegen can emit it into a methodsDefault[]
	// reflection table; runtime VMReflectAnnotations parses it back into a
	// typed value when an annotation member is queried without an explicit
	// override. Static-constant element declarations (`int X = 5;`) are still
	// parsed and dropped because they don't participate in proxy dispatch.
	private static Term AnnotationTypeDeclaration() {
		if (Main.dict.javaVersion < JavaVersion.JLS_50) {
			SemError("@interface (annotation type) requires -source 5 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Term annoName = Identifier();
		Expect(28);
		ObjVector members = new ObjVector();
		while (t.kind != 29 && t.kind != 0) {
			if (t.kind == 9) { Get(); continue; }
			while (t.kind == 10) { TypeUseAnnotationGroup(); }
			while (t.kind == 16 || t.kind == 17 || t.kind == 18
					|| t.kind == 19 || t.kind == 20 || t.kind == 21
					|| t.kind == 22) {
				Get();
			}
			Term elemType;
			if (StartOf(2)) {
				elemType = PrimitiveType();
			} else {
				elemType = SimpleType();
			}
			if (t.kind == 43) { DimSpecSeq(); }
			Term elemName = Identifier();
			if (t.kind == 11) {
				Expect(11);
				Expect(12);
				Term postDim = Empty.newTerm();
				if (t.kind == 43) { postDim = DimSpecSeq(); }
				String defaultText = null;
				if (t.kind == 60) {
					Get();
					defaultText = captureToTopLevelSemi();
				}
				Expect(9);
				MethodDeclaration method = new MethodDeclaration(elemType,
					Empty.newTerm(), elemName, Empty.newTerm(), postDim,
					Empty.newTerm(), new Block());
				if (defaultText != null && defaultText.length() > 0) {
					annotationDefaultsByDecl.put(method, defaultText);
				}
				members.addElement(new TypeDeclaration(Empty.newTerm(),
					method));
			} else {
				if (t.kind == 46) {
					Get();
					skipToTopLevelSemi();
				}
				Expect(9);
			}
		}
		Expect(29);
		Term body = Empty.newTerm();
		if (members.size() > 0) {
			body = (Term) members.elementAt(members.size() - 1);
			for (int i = members.size() - 2; i >= 0; i--) {
				body = new Seq((Term) members.elementAt(i), body);
			}
		}
		body = new Seq(body, Empty.newTerm());
		Term annotationSuper = new ClassOrIfaceType(buildJavaLangAnnoName());
		IfaceDeclaration decl = new IfaceDeclaration(annoName, annotationSuper,
			body);
		synthesizedAnnotationDecls.add(decl);
		return decl;
	}

	// Side channel: synthesized MethodDeclaration for an annotation
	// element -> the raw `default V` text. Read by MethodDeclaration in
	// processPass1 and threaded onto MethodDefinition for codegen.
	private static final ObjHashtable annotationDefaultsByDecl =
		new ObjHashtable();

	static String getAnnotationDefault(Object decl) {
		return decl == null ? null
			: (String) annotationDefaultsByDecl.get(decl);
	}

	// Side channel: IfaceDeclaration instances synthesized for `@interface`
	// declarations. ClassDeclaration.processPass0 consults this to OR the
	// ANNOTATION modifier bit onto ClassDefinition (TODO #2).
	private static final ObjHashSet synthesizedAnnotationDecls =
		new ObjHashSet();

	static boolean isSynthesizedAnnotationType(Term decl) {
		return decl != null && synthesizedAnnotationDecls.contains(decl);
	}

	private static void skipToTopLevelSemi() {
		int braceDepth = 0, parenDepth = 0;
		while (t.kind != 0) {
			if (t.kind == 9 && braceDepth == 0 && parenDepth == 0) {
				return;
			}
			if (t.kind == 28) braceDepth++;
			else if (t.kind == 29) braceDepth--;
			else if (t.kind == 11) parenDepth++;
			else if (t.kind == 12) parenDepth--;
			Get();
		}
	}

	private static String captureToTopLevelSemi() {
		StringBuffer raw = new StringBuffer();
		int braceDepth = 0, parenDepth = 0;
		while (t.kind != 0) {
			if (t.kind == 9 && braceDepth == 0 && parenDepth == 0) {
				break;
			}
			if (t.kind == 28) braceDepth++;
			else if (t.kind == 29) braceDepth--;
			else if (t.kind == 11) parenDepth++;
			else if (t.kind == 12) parenDepth--;
			if (raw.length() > 0
					&& isWordChar(raw.charAt(raw.length() - 1))
					&& isWordCharStart(t.val)) {
				raw.append(' ');
			}
			raw.append(t.val);
			Get();
		}
		return raw.toString().trim();
	}

	private static Term buildJavaLangAnnoName() {
		Term tail = new QualifiedName(new LexTerm(LexTerm.ID, "Annotation"),
			Empty.newTerm());
		tail = new QualifiedName(new LexTerm(LexTerm.ID, "annotation"), tail);
		tail = new QualifiedName(new LexTerm(LexTerm.ID, "lang"), tail);
		return new QualifiedName(new LexTerm(LexTerm.ID, "java"), tail);
	}

	private static Term ClassModifierSeq() {
		Term z;
		Term a, b = null;
		a = ClassModifier();
		if ((StartOf(29) && !(t.kind == 10 && peek(2).kind == 24))
				|| looksLikeSealedKw()) {
			b = ClassModifierSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term ClassInterfaceDeclaration() {
		Term z;
		Term a = Empty.term, b;
		int annoSnap = snapshotPendingAnnotations();
		if ((StartOf(29) && !(t.kind == 10 && peek(2).kind == 24))
				|| looksLikeSealedKw()) {
			a = ClassModifierSeq();
		}
		b = ClassDeclOrInterfaceDecl();
		// Slice 49: attach declaration-annotation type names to the
		// inner declaration (b). Snapshot semantics ensure the body's
		// member parses don't steal these.
		recordAnnotations(b, takePendingAnnotationsSince(annoSnap));
		z = new TypeDeclaration(a, b);
		return z;
	}

	private static Term TypeDeclaration() {
		Term z;
		z = Empty.term;
		if (t.kind == 9) {
			Get();
		} else if (StartOf(30)) {
			z = ClassInterfaceDeclaration();
		} else Error(153);
		return z;
	}

	private static Term StarOrIdentOptImportDeclSpec() {
		Term z;
		z = Empty.term;
		if (t.kind == 15) {
			Get();
			z = new LexTerm(LexTerm.TIMES, token.val);
		} else if (t.kind == 1 || t.kind == 7) {
			z = IdentOptImportDeclSpec();
		} else Error(154);
		return z;
	}

	private static Term IdentOptImportDeclSpec() {
		Term z;
		Term a, c = Empty.term;
		a = Identifier();
		if (t.kind == 13) {
			Get();
			c = StarOrIdentOptImportDeclSpec();
		}
		z = new QualifiedName(a, c);
		return z;
	}

	private static Term ImportDeclaration() {
		Term z;
		Term b;
		boolean isStatic = false;
		Expect(14);
		if (t.kind == 19) {
			Get();
			isStatic = true;
		}
		b = IdentOptImportDeclSpec();
		Expect(9);
		ImportDeclaration imp = new ImportDeclaration(b);
		if (isStatic) imp.setStatic();
		z = imp;
		return z;
	}

	private static Term QualifiedIdentifierOrString() {
		Term z;
		z = Empty.term;
		if (t.kind == 5) {
			Get();
			z = new StringLiteral(token.val);
		} else if (t.kind == 1 || t.kind == 7) {
			z = QualifiedIdentifier();
		} else Error(155);
		return z;
	}

	private static void Annotation() {
		Expect(10);
		if (Main.dict.javaVersion < JavaVersion.JLS_50) {
			SemError("annotation requires -source 5 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		Term qname = QualifiedIdentifier();
		// Slice 47: `@SafeVarargs` is a Java 7 standard annotation
		// (java.lang.SafeVarargs). JCGO has no warning channel for
		// the unchecked-conversion warning it suppresses, so the
		// runtime effect is a no-op — but the version gate stays so
		// the syntax is rejected on older source levels.
		String dotted = qname instanceof QualifiedName
				? ((QualifiedName) qname).dottedName() : "";
		if (("SafeVarargs".equals(dotted)
				|| "java.lang.SafeVarargs".equals(dotted))
				&& Main.dict.javaVersion < JavaVersion.JLS_70) {
			SemError("@SafeVarargs requires -source 7 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		boolean shouldCapture = !inTypeUseAnnotationContext
				&& dotted.length() > 0
				&& !isSourceRetentionAnnotation(dotted);
		// Walk the optional balanced-paren argument content. Slice 86
		// follow-up: also reconstruct the raw textual form from the
		// token stream so runtime proxies can answer the simple
		// `@Anno("x")` / `@Anno(value="x")` cases.
		String argText = null;
		if (t.kind == 11) {
			Get();
			StringBuffer raw = shouldCapture ? new StringBuffer() : null;
			int depth = 1;
			while (depth > 0 && t.kind != 0) {
				if (t.kind == 11) {
					depth++;
					if (raw != null) raw.append('(');
				} else if (t.kind == 12) {
					depth--;
					if (depth == 0) break;
					if (raw != null) raw.append(')');
				} else if (raw != null) {
					if (raw.length() > 0
							&& isWordChar(raw.charAt(raw.length() - 1))
							&& isWordCharStart(t.val)) {
						raw.append(' ');
					}
					raw.append(t.val);
				}
				Get();
			}
			Expect(12);
			if (raw != null) {
				argText = raw.toString().trim();
			}
		}
		// Slice 49: capture the annotation type name for the
		// surrounding declaration (skipped for type-use positions).
		// Slice 49 follow-up (retention): well-known SOURCE-retention
		// annotations are not retained — they exist only for the
		// compiler's benefit and shouldn't surface in runtime
		// reflection data. Other RetentionPolicy values aren't
		// distinguishable at parse time without loading the
		// annotation class.
		if (shouldCapture) {
			pendingAnnotationNames.addElement(dotted);
			pendingAnnotationArgs.addElement(argText != null ? argText : "");
		}
	}

	// Slice 86 follow-up: parallel to pendingAnnotationNames; each
	// entry is the raw textual paren content of the captured
	// annotation (e.g. `value="x"` for `@Anno(value="x")`) or "" when
	// the annotation has no arguments.
	private static ObjVector pendingAnnotationArgs = new ObjVector();

	// Word-boundary heuristics for whitespace between concatenated
	// token values when reconstructing the raw arg text.
	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '$';
	}

	private static boolean isWordCharStart(String s) {
		return s != null && s.length() > 0 && isWordChar(s.charAt(0));
	}

	// Slice 49: well-known SOURCE-retention annotations skipped from
	// runtime metadata. Per JLS the @Retention(SOURCE) annotations
	// shouldn't be reflectable; without parsing @Retention argument
	// values we hardcode the standard ones JCGO is likely to see.
	private static boolean isSourceRetentionAnnotation(String dotted) {
		return "Override".equals(dotted)
			|| "java.lang.Override".equals(dotted)
			|| "SuppressWarnings".equals(dotted)
			|| "java.lang.SuppressWarnings".equals(dotted);
	}

	private static void AnnotationGroup() {
		Annotation();
		if (t.kind == 10) {
			AnnotationGroup();
		}
	}

	private static Term QualifiedIdentifier() {
		Term z;
		Term a, c = Empty.term;
		a = Identifier();
		// Stop at `..` so the varargs ellipsis (`...`) isn't eaten as a
		// qualified-name continuation — the caller (FormalParam) handles it.
		if (t.kind == 13 && peek(2).kind != 13) {
			Get();
			c = QualifiedIdentifier();
		}
		z = new QualifiedName(a, c);
		return z;
	}

	private static Term Identifier() {
		Term z;
		z = Empty.term;
		if (t.kind == 7 || t.kind == 1) {
			Get();
			// Slice 35: `_` was reserved as an identifier in JLS 9
			// (JEP 213). A single hook here covers declarators,
			// references, parameters, lambda params, and catch params
			// since JCGO routes all of them through Identifier().
			// Skipped for files in the bundled stdlib (classpath-0.93
			// and goclsp) — those are upstream sources we don't
			// rewrite, and they predate the Java 9 ban; they'd fail
			// the gate even though no user code triggered the issue.
			if ("_".equals(token.val)
					&& Main.dict.javaVersion >= JavaVersion.JLS_90
					&& !isStdlibSource()) {
				SemError("`_` is reserved and is not allowed as an "
					+ "identifier (got -source "
					+ JavaVersion.format(Main.dict.javaVersion) + ")");
			}
			z = new LexTerm(LexTerm.ID, token.val);
		} else Error(156);
		return z;
	}

	private static boolean isStdlibSource() {
		String fn = Scanner.err != null ? Scanner.err.fileName : null;
		if (fn == null) return false;
		return fn.indexOf("classpath-0.93") >= 0
			|| fn.indexOf("goclsp") >= 0;
	}

	private static Term TypeDeclarationSeq() {
		Term z;
		Term a, b = null;
		a = TypeDeclaration();
		if (StartOf(31)) {
			b = TypeDeclarationSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term ImportDeclarationSeq() {
		Term z;
		Term a, b = null;
		a = ImportDeclaration();
		if (t.kind == 14) {
			b = ImportDeclarationSeq();
		}
		z = b != null ? new Seq(a, b) : a;
		return z;
	}

	private static Term PackageSpecifier() {
		Term z;
		Term b;
		Expect(8);
		b = QualifiedIdentifier();
		Expect(9);
		z = new PackageDeclaration(b);
		return z;
	}

	private static void comivmaisoftjcgo() {
		Term a = Empty.term, b = Empty.term, c = Empty.newTerm();
		if (t.kind == 8) {
			a = PackageSpecifier();
		}
		if (t.kind == 14) {
			b = ImportDeclarationSeq();
		}
		// Slice 48 (Java 9): module-info.java — `module com.foo { ... }`
		// or `open module com.foo { ... }`. Parse + discard so projects
		// that ship a module-info alongside their sources don't choke
		// JCGO. Annotations on a top-level class are handled by
		// ClassInterfaceDeclaration's modifier seq, so we deliberately
		// don't pre-consume them (slice 49 needs them visible to the
		// class for runtime retention). Annotated module declarations
		// like `@Deprecated module foo {}` aren't supported — that's
		// a rare edge case worth deferring.
		if (looksLikeModuleDecl()) {
			consumeModuleDeclaration();
		} else {
			c = TypeDeclarationSeq();
		}
		if (t.kind == 6) {
			Get();
		}
		Expect(0);
		new CompilationUnit(a, b, c);
	}

	// Slice 48: `module` is a restricted keyword (still ID-token in the
	// lexer), recognized only at compilation-unit position. `open`
	// optionally precedes it.
	private static boolean looksLikeModuleDecl() {
		if (t.kind == 1 && "module".equals(t.val)) return true;
		return t.kind == 1 && "open".equals(t.val)
			&& peek(2).kind == 1 && "module".equals(peek(2).val);
	}

	private static void consumeModuleDeclaration() {
		if (Main.dict.javaVersion < JavaVersion.JLS_90) {
			SemError("module declaration requires -source 9 or higher (got "
				+ JavaVersion.format(Main.dict.javaVersion) + ")");
		}
		if (t.kind == 1 && "open".equals(t.val)) Get();
		Get();  // `module`
		QualifiedIdentifier();
		Expect(28);
		int depth = 1;
		while (depth > 0 && t.kind != 0) {
			if (t.kind == 28) {
				depth++;
			} else if (t.kind == 29) {
				depth--;
				if (depth == 0) break;
			}
			Get();
		}
		Expect(29);
	}



	static void Parse() {
		t = new Token();
		Get();
		comivmaisoftjcgo();

	}

	private static boolean[][] set = {
	{T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,T,T, T,T,x,T, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,T, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,x,x, x,x,x,x, x,x},
	{x,T,T,T, T,T,x,T, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x},
	{x,T,T,T, T,T,x,T, x,x,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,T, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,T,T, T,T,x,T, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,T, T,T,T,T, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, T,T,T,T, T,T,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, x,x,x,x, x,x,T,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, T,T,T,T, T,T,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,x,x, x,x,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, x,x,x,x, x,x,T,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, T,T,T,T, T,T,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,T,T, T,T,x,T, x,T,x,T, x,x,x,x, x,x,x,T, T,T,T,T, x,x,x,x, T,x,T,x, x,x,T,T, T,T,T,T, T,T,T,x, x,x,x,T, T,T,T,T, T,T,T,T, T,x,x,x,
	 x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,T, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, x,x,x,x, x,x,T,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, T,T,T,T, T,T,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,T,T, T,T,x,T, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,T, x,x},
	{x,T,T,T, T,T,x,T, x,x,T,T, x,x,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,T, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,T,T, T,T,x,T, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,x,x,x, x,x,T,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,T, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, x,x,x,x, x,x,T,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, T,T,T,T, T,T,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,x,x, x,x,x,T, x,x,T,x, x,x,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,x,x,x, x,x,x,x, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,T,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,x,x, x,x,x,T, x,x,x,x, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,T,T, T,T,x,T, x,T,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,x,T,x, x,x,T,T, T,T,T,T, T,T,T,x, x,x,x,T, T,T,T,T, T,T,T,T, T,x,x,x,
	 x,x,x,x, x,x,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,T, T,T,T,T, x,x},
	{x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, T,T,T,T, T,T,T,x, x,x,x,x, x,x,T,T, T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,x,x, x,x,x,T, x,x,T,x, x,x,x,x, T,T,T,T, T,T,T,T, T,x,x,x, T,x,T,T, T,T,T,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,T,x,x, x,x,x,T, x,T,T,x, x,x,x,x, T,T,T,T, T,T,T,T, T,x,x,x, T,x,T,T, T,T,T,T, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, T,T,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, T,T,T,T, T,T,T,T, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x},
	{x,x,x,x, x,x,x,x, x,T,T,x, x,x,x,x, T,T,T,T, T,T,T,T, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x,
	 x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x}

	};
}
