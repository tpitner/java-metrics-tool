// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.parser;

import cz.utulnykoutek.metrics.domain.*;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parses Java source files into the domain model using Eclipse JDT.
 * T1: captures identity, relations, dependency classification and binding coverage.
 */
public class JdtParser {
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "\\bpublic\\s+(?:(?:abstract|final|sealed|non-sealed|static|strictfp)\\s+)*"
                    + "(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][\\w$]*)\\b");

    public static final class Config {
        public List<Path> classpath = new ArrayList<>();
        public List<Path> sourcepath = new ArrayList<>();
        public String release = null;
        public boolean resolveBindings = true;
        public Config() {}
    }

    private final Config config;

    public JdtParser() { this(new Config()); }
    public JdtParser(Config config) { this.config = config == null ? new Config() : config; }

    public Compound parse(Path file) throws IOException, SyntaxErrorException {
        String source = java.nio.file.Files.readString(file);
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        String unitName = file.getFileName().toString();
        Matcher publicType = PUBLIC_TYPE.matcher(source);
        parser.setUnitName(publicType.find() ? publicType.group(1) + ".java" : unitName);
        parser.setResolveBindings(config.resolveBindings);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);

        List<String> cp = new ArrayList<>();
        for (Path p : config.classpath) cp.add(p.toString());
        List<String> sp = new ArrayList<>();
        for (Path p : config.sourcepath) sp.add(p.toString());
        parser.setEnvironment(cp.toArray(new String[0]), sp.toArray(new String[0]), null, true);

        if (config.release != null && !config.release.isBlank()) {
            Map<String, String> options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(config.release, options);
            parser.setCompilerOptions(options);
        }

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        checkSyntaxErrors(cu, source, file.getFileName().toString());

        String packageName = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() : "";
        String unitId = file.toAbsolutePath().normalize().toString();
        String pkgSymbol = "p:" + packageName;
        PackageCompound pkg = new PackageCompound("pkg:" + packageName, pkgSymbol, packageName, packageName, true);
        pkg.sourceFiles.add(file.toAbsolutePath().normalize());

        ParseCtx ctx = new ParseCtx(pkg, packageName, unitId, source);
        for (Object o : cu.types()) {
            if (o instanceof AbstractTypeDeclaration td) {
                ctx.parseTopLevel(td);
            }
        }
        // local/anonymous inside all type bodies
        for (TypeCompound t : List.copyOf(ctx.allTypes)) {
            ctx.collectLocalAndAnonymous(t, findDeclaration(cu, t));
        }
        // relations and override detection
        ctx.buildTypeRelations();
        ctx.finalizeCoverage();
        return pkg;
    }

    private void checkSyntaxErrors(CompilationUnit cu, String source, String fileName) throws SyntaxErrorException {
        List<String> syntaxErrors = new ArrayList<>();
        char[] src = source.toCharArray();
        for (Object p : cu.getProblems()) {
            if (p instanceof org.eclipse.jdt.core.compiler.IProblem prob && prob.isError()) {
                String msg = prob.getMessage();
                if (msg != null && msg.contains("cannot be resolved")) continue;
                int line = prob.getSourceLineNumber();
                int start = prob.getSourceStart();
                int lineStart = 0;
                for (int j = start; j > 0; j--) { if (src[j - 1] == '\n') { lineStart = j; break; } }
                int col = start - lineStart + 1;
                if (line <= 0) line = 1;
                if (col <= 0) col = 1;
                syntaxErrors.add(fileName + ":" + line + ":" + col + ": " + msg);
            }
        }
        if (!syntaxErrors.isEmpty()) throw new SyntaxErrorException(syntaxErrors);
    }

    private AbstractTypeDeclaration findDeclaration(CompilationUnit cu, TypeCompound t) {
        // best-effort: match by qualified simple path
        String simplePath = t.qualifiedName;
        if (!t.qualifiedName.startsWith(cu.getPackage() == null ? "" : cu.getPackage().getName().getFullyQualifiedName())) {
            // ignore
        }
        String pkg = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() : "";
        String rel = pkg.isEmpty() ? t.qualifiedName : (t.qualifiedName.startsWith(pkg + ".") ? t.qualifiedName.substring(pkg.length() + 1) : t.qualifiedName);
        return findByPath(cu.types(), rel);
    }

    @SuppressWarnings("unchecked")
    private AbstractTypeDeclaration findByPath(List types, String path) {
        if (path == null) return null;
        String head = path;
        String tail = null;
        int dot = path.indexOf('.');
        if (dot >= 0) { head = path.substring(0, dot); tail = path.substring(dot + 1); }
        // handle local/anon markers in path by fallback search
        for (Object o : types) {
            if (!(o instanceof AbstractTypeDeclaration td)) continue;
            if (td.getName().getIdentifier().equals(head)) {
                if (tail == null) return td;
                return findByPath(td.bodyDeclarations(), tail);
            }
        }
        // fallback: search any match
        for (Object o : types) {
            if (o instanceof AbstractTypeDeclaration td) {
                AbstractTypeDeclaration found = findByPath(td.bodyDeclarations(), path);
                if (found != null) return found;
            }
        }
        return null;
    }

    static final class ParseCtx {
        final PackageCompound pkg;
        final String packageName;
        final String unitId;
        final String source;
        final List<TypeCompound> allTypes = new ArrayList<>();
        final Map<AbstractTypeDeclaration, TypeCompound> typeMap = new IdentityHashMap<>();
        final Map<String, Integer> localSeq = new HashMap<>();
        final Map<String, Integer> anonSeq = new HashMap<>();
        final Map<String, Integer> initializerSeq = new HashMap<>();
        final Map<String, Integer> lambdaSeq = new HashMap<>();

        ParseCtx(PackageCompound pkg, String packageName, String unitId, String source) {
            this.pkg = pkg; this.packageName = packageName; this.unitId = unitId;
            this.source = source;
        }

        void parseTopLevel(AbstractTypeDeclaration td) {
            parseType(td, pkg, "", null);
        }

        void parseType(AbstractTypeDeclaration decl, Compound parent, String enclosingPath, String declaredInMemberId) {
            CompoundKind kind = kindOf(decl);
            String simpleName = decl.getName() == null ? "" : decl.getName().getIdentifier();
            ITypeBinding tb = decl.resolveBinding();
            String path = enclosingPath.isEmpty() ? simpleName : enclosingPath + "." + simpleName;
            String qualifiedName = qualifiedFromBinding(tb, packageName, path);
            SymbolRef selfRef = symbolForTypeBinding(tb, decl, parent, path);
            String id = "type:" + packageName + ":" + path;
            String symbolId = selfRef.id != null ? selfRef.id : "ts:" + unitId + ":" + path;

            EnumSet<cz.utulnykoutek.metrics.domain.Modifier> modifiers = toModifiers(decl.getModifiers());
            NestingKind nesting = nestingOf(decl, parent, modifiers, kind);

            TypeCompound type = new TypeCompound(id, symbolId, kind, simpleName, qualifiedName, parent, modifiers, nesting, selfRef.resolved, selfRef.bindingKey);
            type.sourceSpan = sourceSpan(decl);
            type.declaredInMemberId = declaredInMemberId;
            parent.addChild(type);
            allTypes.add(type);
            typeMap.put(decl, type);

            pkg.coverage.counter("typeDeclarations").addAttempt(selfRef.resolved, false);
            if (!selfRef.resolved) sample("typeDeclarations", parent == null ? pkg.symbolId : parent.id, simpleName, "declaration", reason(tb));

            fillSupertypesAndTypeParams(type, decl);
            fillMembersAndNested(type, decl, path);
            rebuildDeps(type);
        }

        void fillSupertypesAndTypeParams(TypeCompound type, AbstractTypeDeclaration decl) {
            if (decl instanceof TypeDeclaration td) {
                if (td.getSuperclassType() != null) {
                    SymbolRef sr = refForType(td.getSuperclassType(), type, DependencyUsage.EXTENDS);
                    type.superclass = sr;
                    pkg.coverage.counter("superTypes").addAttempt(sr.resolved, false);
                    type.dependencyEdges.add(new DependencyEdge(type.symbolId, sr, DependencyUsage.EXTENDS));
                }
                for (Object o : td.superInterfaceTypes()) {
                    if (o instanceof Type t) {
                        SymbolRef sr = refForType(t, type, DependencyUsage.IMPLEMENTS);
                        type.interfaces.add(sr);
                        pkg.coverage.counter("implementedInterfaces").addAttempt(sr.resolved, false);
                        type.dependencyEdges.add(new DependencyEdge(type.symbolId, sr, DependencyUsage.IMPLEMENTS));
                    }
                }
                for (Object o : td.typeParameters()) {
                    if (o instanceof TypeParameter tp) type.typeParameters.add(typeParam(tp, type));
                }
            } else if (decl instanceof EnumDeclaration ed) {
                for (Object o : ed.superInterfaceTypes()) {
                    if (o instanceof Type t) {
                        SymbolRef sr = refForType(t, type, DependencyUsage.IMPLEMENTS);
                        type.interfaces.add(sr);
                        pkg.coverage.counter("implementedInterfaces").addAttempt(sr.resolved, false);
                        type.dependencyEdges.add(new DependencyEdge(type.symbolId, sr, DependencyUsage.IMPLEMENTS));
                    }
                }
            } else if (decl instanceof RecordDeclaration rd) {
                for (Object o : rd.superInterfaceTypes()) {
                    if (o instanceof Type t) {
                        SymbolRef sr = refForType(t, type, DependencyUsage.IMPLEMENTS);
                        type.interfaces.add(sr);
                        pkg.coverage.counter("implementedInterfaces").addAttempt(sr.resolved, false);
                        type.dependencyEdges.add(new DependencyEdge(type.symbolId, sr, DependencyUsage.IMPLEMENTS));
                    }
                }
                for (Object o : rd.typeParameters()) {
                    if (o instanceof TypeParameter tp) type.typeParameters.add(typeParam(tp, type));
                }
                // record components as fields-like members? spec allows; count/represent as fields for utility
                for (Object o : rd.recordComponents()) {
                    if (o instanceof SingleVariableDeclaration svd) {
                        SymbolRef ft = refForType(svd.getType(), type, DependencyUsage.FIELD);
                        Field f = new Field(memberFallbackId(type, "field", svd.getName().getIdentifier()), null, type,
                                svd.getName().getIdentifier(), EnumSet.of(cz.utulnykoutek.metrics.domain.Modifier.PRIVATE, cz.utulnykoutek.metrics.domain.Modifier.FINAL), ft, ft.resolved);
                        type.addMember(f);
                        pkg.coverage.counter("memberDeclarations").addAttempt(ft.resolved, false);
                        pkg.coverage.counter("memberTypeRefs").addAttempt(ft.resolved, false);
                        type.dependencyEdges.add(new DependencyEdge(type.symbolId, ft, DependencyUsage.FIELD));
                    }
                }
            } else if (decl instanceof AnnotationTypeDeclaration) {
                // members handled in body
            }
        }

        TypeParameterDecl typeParam(TypeParameter tp, TypeCompound owner) {
            TypeParameterDecl d = new TypeParameterDecl(tp.getName().getIdentifier());
            for (Object b : tp.typeBounds()) {
                if (b instanceof Type t) {
                    SymbolRef sr = refForType(t, owner, DependencyUsage.TYPE_PARAM_BOUND);
                    d.bounds.add(sr);
                    owner.dependencyEdges.add(new DependencyEdge(owner.symbolId, sr, DependencyUsage.TYPE_PARAM_BOUND));
                }
            }
            return d;
        }

        @SuppressWarnings("unchecked")
        void fillMembersAndNested(TypeCompound type, AbstractTypeDeclaration decl, String path) {
            Map<Method, MethodDeclaration> cohesionMethods = new LinkedHashMap<>();
            for (Object o : decl.bodyDeclarations()) {
                if (o instanceof MethodDeclaration md) {
                    Member member = parseMethodLike(type, md);
                    if (member instanceof Method method) cohesionMethods.put(method, md);
                    collectLambdas(type, md, member.id);
                } else if (o instanceof FieldDeclaration fd) {
                    SymbolRef ft = refForType(fd.getType(), type, DependencyUsage.FIELD);
                    type.dependencyEdges.add(new DependencyEdge(type.symbolId, ft, DependencyUsage.FIELD));
                    for (Object f : fd.fragments()) {
                        if (f instanceof VariableDeclarationFragment frag) {
                            EnumSet<cz.utulnykoutek.metrics.domain.Modifier> fmods = toModifiers(fd.getModifiers());
                            String name = frag.getName().getIdentifier();
                            IVariableBinding vb = frag.resolveBinding();
                            String id = memberId(vb, type, "field", name, null);
                            Field field = new Field(id, vb == null ? null : vb.getKey(), type, name, fmods, ft, vb != null || ft.resolved);
                            type.addMember(field);
                            if (frag.getInitializer() != null) {
                                collectLambdas(type, frag.getInitializer(), field.id);
                            }
                            pkg.coverage.counter("memberDeclarations").addAttempt(vb != null, false);
                            pkg.coverage.counter("memberTypeRefs").addAttempt(ft.resolved, false);
                        }
                    }
                } else if (o instanceof EnumConstantDeclaration ecd) {
                    String name = ecd.getName().getIdentifier();
                    IVariableBinding vb = ecd.resolveVariable();
                    SymbolRef ownerRef = selfSymbolRef(type);
                    String id = memberId(vb, type, "enumconst", name, null);
                    EnumConstant ec = new EnumConstant(id, vb == null ? null : vb.getKey(), type, name,
                            EnumSet.of(cz.utulnykoutek.metrics.domain.Modifier.PUBLIC, cz.utulnykoutek.metrics.domain.Modifier.STATIC, cz.utulnykoutek.metrics.domain.Modifier.FINAL),
                            vb != null, ownerRef);
                    type.addMember(ec);
                    collectLambdas(type, ecd, ec.id);
                    pkg.coverage.counter("memberDeclarations").addAttempt(vb != null, false);
                } else if (o instanceof Initializer initializer) {
                    parseInitializer(type, initializer);
                } else if (o instanceof AbstractTypeDeclaration nested) {
                    parseType(nested, type, path, null);
                }
            }
            type.cohesionEvidence = CohesionEvidenceExtractor.extract(type, cohesionMethods);
            collectBodyDependencies(type, decl.bodyDeclarations());
        }

        Member parseMethodLike(TypeCompound type, MethodDeclaration md) {
            boolean ctor = md.isConstructor();
            String name = md.getName().getIdentifier();
            List<Parameter> params = new ArrayList<>();
            int idx = 0;
            for (Object p : md.parameters()) {
                if (p instanceof SingleVariableDeclaration svd) {
                    SymbolRef pt = refForType(svd.getType(), type, DependencyUsage.METHOD_PARAM);
                    type.dependencyEdges.add(new DependencyEdge(type.symbolId, pt, DependencyUsage.METHOD_PARAM));
                    params.add(new Parameter(svd.getName().getIdentifier(), pt, svd.isVarargs(), idx++));
                    pkg.coverage.counter("memberTypeRefs").addAttempt(pt.resolved, false);
                }
            }
            List<SymbolRef> thrown = new ArrayList<>();
            for (Object t : md.thrownExceptionTypes()) {
                if (t instanceof Type ty) {
                    SymbolRef tr = refForType(ty, type, DependencyUsage.THROWS);
                    thrown.add(tr);
                    type.dependencyEdges.add(new DependencyEdge(type.symbolId, tr, DependencyUsage.THROWS));
                    pkg.coverage.counter("memberTypeRefs").addAttempt(tr.resolved, false);
                }
            }
            IMethodBinding mb = md.resolveBinding();
            String sig = signatureKey(name, params, ctor ? null : returnDisplay(md));
            String id = memberId(mb, type, ctor ? "constructor" : "method", name, sig);
            boolean mres = mb != null;
            pkg.coverage.counter("memberDeclarations").addAttempt(mres, false);

            if (ctor) {
                Constructor c = new Constructor(id, mb == null ? null : mb.getKey(), type, name, toModifiers(md.getModifiers()), params, thrown, mres);
                c.sourceSpan = sourceSpan(md);
                type.addMember(c);
                type.cyclomaticComplexityEvidence.add(complexityEvidence(
                        c.id, ExecutableKind.CONSTRUCTOR, md.getBody()));
                return c;
            } else {
                SymbolRef rt = md.getReturnType2() == null ? primitiveRef("void") : refForType(md.getReturnType2(), type, DependencyUsage.METHOD_RETURN);
                type.dependencyEdges.add(new DependencyEdge(type.symbolId, rt, DependencyUsage.METHOD_RETURN));
                pkg.coverage.counter("memberTypeRefs").addAttempt(rt.resolved, false);
                Method m = new Method(id, mb == null ? null : mb.getKey(), type, name, toModifiers(md.getModifiers()), rt, params, thrown, mres);
                m.sourceSpan = sourceSpan(md);
                type.addMember(m);
                type.cyclomaticComplexityEvidence.add(complexityEvidence(
                        m.id, ExecutableKind.METHOD, md.getBody()));
                return m;
            }
        }

        void parseInitializer(TypeCompound type, Initializer initializer) {
            boolean isStatic = org.eclipse.jdt.core.dom.Modifier.isStatic(initializer.getModifiers());
            String category = isStatic ? "static" : "instance";
            int sequence = initializerSeq.merge(type.symbolId + ":" + category, 1, Integer::sum);
            String id = type.symbolId + "#<initializer:" + category + ":" + sequence + ">";
            InitializerBlock block = new InitializerBlock(id, type, isStatic, sourceSpan(initializer));
            type.initializers.add(block);
            type.cyclomaticComplexityEvidence.add(complexityEvidence(
                    id, ExecutableKind.INITIALIZER, initializer.getBody()));
            collectLambdas(type, initializer, id);
        }

        void collectLambdas(TypeCompound type, ASTNode root, String enclosingSubjectId) {
            Deque<String> enclosing = new ArrayDeque<>();
            enclosing.push(enclosingSubjectId);
            root.accept(new ASTVisitor() {
                @Override public boolean preVisit2(ASTNode node) {
                    if (node == root) return true;
                    return !(node instanceof AbstractTypeDeclaration)
                            && !(node instanceof AnonymousClassDeclaration);
                }

                @Override public boolean visit(LambdaExpression node) {
                    int sequence = lambdaSeq.merge(type.symbolId, 1, Integer::sum);
                    String id = type.symbolId + "#<lambda:" + sequence + ">";
                    type.lambdas.add(new LambdaExpressionEntity(
                            id, type, enclosing.peek(), sourceSpan(node)));
                    type.cyclomaticComplexityEvidence.add(complexityEvidence(
                            id, ExecutableKind.LAMBDA, node.getBody()));
                    enclosing.push(id);
                    return true;
                }

                @Override public void endVisit(LambdaExpression node) {
                    enclosing.pop();
                }
            });
        }

        CyclomaticComplexityEvidence complexityEvidence(
                String subjectId, ExecutableKind kind, ASTNode body) {
            if (body == null) {
                return new CyclomaticComplexityEvidence(subjectId, kind, false, 0);
            }
            AtomicInteger decisions = new AtomicInteger();
            body.accept(new ASTVisitor() {
                @Override public boolean preVisit2(ASTNode node) {
                    if (node == body) return true;
                    return !(node instanceof LambdaExpression)
                            && !(node instanceof AbstractTypeDeclaration)
                            && !(node instanceof AnonymousClassDeclaration);
                }

                @Override public boolean visit(IfStatement node) { decisions.incrementAndGet(); return true; }
                @Override public boolean visit(ForStatement node) { decisions.incrementAndGet(); return true; }
                @Override public boolean visit(EnhancedForStatement node) { decisions.incrementAndGet(); return true; }
                @Override public boolean visit(WhileStatement node) { decisions.incrementAndGet(); return true; }
                @Override public boolean visit(DoStatement node) { decisions.incrementAndGet(); return true; }
                @Override public boolean visit(CatchClause node) { decisions.incrementAndGet(); return true; }
                @Override public boolean visit(ConditionalExpression node) { decisions.incrementAndGet(); return true; }
                @Override public boolean visit(SwitchCase node) {
                    if (!node.isDefault()) decisions.incrementAndGet();
                    return true;
                }
                @Override public boolean visit(GuardedPattern node) { decisions.incrementAndGet(); return true; }
                @Override public boolean visit(InfixExpression node) {
                    if (node.getOperator() == InfixExpression.Operator.CONDITIONAL_AND
                            || node.getOperator() == InfixExpression.Operator.CONDITIONAL_OR) {
                        decisions.addAndGet(1 + node.extendedOperands().size());
                    }
                    return true;
                }
            });
            return new CyclomaticComplexityEvidence(subjectId, kind, true, decisions.get());
        }

        String returnDisplay(MethodDeclaration md) { return md.getReturnType2() == null ? "void" : md.getReturnType2().toString(); }

        String signatureKey(String name, List<Parameter> params, String ret) {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append("(");
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append(",");
                SymbolRef t = params.get(i).type;
                sb.append(t == null ? "?" : (t.qualifiedName != null ? t.qualifiedName : t.displayName));
                if (params.get(i).varargs) sb.append("...");
            }
            sb.append(")");
            if (ret != null) sb.append(":").append(ret);
            return sb.toString();
        }

        void collectLocalAndAnonymous(TypeCompound owner, AbstractTypeDeclaration decl) {
            if (decl == null) return;
            decl.accept(new ASTVisitor() {
                String currentScopeId() { return owner.symbolId; }
                @Override public boolean preVisit2(ASTNode node) {
                    return !(node instanceof AbstractTypeDeclaration nested) || nested == decl;
                }
                @Override public boolean visit(TypeDeclarationStatement node) {
                    if (node.getDeclaration() != null) {
                        parseLocalType(node.getDeclaration(), owner, owner.qualifiedName, null);
                    }
                    return false;
                }
                @Override public boolean visit(AnonymousClassDeclaration node) {
                    parseAnonymous(node, owner, owner.qualifiedName, null);
                    return false;
                }
            });
        }

        void parseLocalType(AbstractTypeDeclaration decl, Compound parent, String enclosingPath, String declaredInMemberId) {
            String simple = decl.getName() == null ? "" : decl.getName().getIdentifier();
            String scope = parent.symbolId;
            int seq = localSeq.merge(scope + ":local:" + simple, 1, Integer::sum);
            String path = enclosingPath + "$local" + seq + ":" + simple;
            CompoundKind kind = kindOf(decl);
            ITypeBinding tb = decl.resolveBinding();
            String qualifiedName = qualifiedFromBinding(tb, packageName, path);
            SymbolRef selfRef = symbolForTypeBinding(tb, decl, parent, path);
            String id = "type:" + packageName + ":" + path;
            String symbolId = selfRef.id != null ? selfRef.id : "ts:" + unitId + ":" + path;
            EnumSet<cz.utulnykoutek.metrics.domain.Modifier> modifiers = toModifiers(decl.getModifiers());
            NestingKind nesting = NestingKind.LOCAL;
            TypeCompound type = new TypeCompound(id, symbolId, kind, simple, qualifiedName, parent, modifiers, nesting, selfRef.resolved, selfRef.bindingKey);
            type.sourceSpan = sourceSpan(decl);
            type.declaredInMemberId = declaredInMemberId;
            parent.addChild(type);
            allTypes.add(type);
            typeMap.put(decl, type);
            pkg.coverage.counter("typeDeclarations").addAttempt(selfRef.resolved, false);
            pkg.coverage.counter("localAndAnonymousTypes").addAttempt(selfRef.resolved, false);
            fillSupertypesAndTypeParams(type, decl);
            fillMembersAndNested(type, decl, path);
            collectLocalAndAnonymous(type, decl);
            rebuildDeps(type);
        }

        void parseAnonymous(AnonymousClassDeclaration decl, Compound parent, String enclosingPath, String declaredInMemberId) {
            String scope = parent.symbolId;
            int seq = anonSeq.merge(scope + ":anon", 1, Integer::sum);
            String path = enclosingPath + "$anon" + seq;
            ITypeBinding tb = decl.resolveBinding();
            SymbolRef selfRef = symbolForAnonBinding(tb, path);
            String id = "type:" + packageName + ":" + path;
            String symbolId = selfRef.id != null ? selfRef.id : "ts:" + unitId + ":" + path;
            String qualifiedName = qualifiedFromBinding(tb, packageName, path);
            TypeCompound type = new TypeCompound(id, symbolId, CompoundKind.CLASS, "", qualifiedName, parent, EnumSet.noneOf(cz.utulnykoutek.metrics.domain.Modifier.class), NestingKind.ANONYMOUS, selfRef.resolved, selfRef.bindingKey);
            type.sourceSpan = sourceSpan(decl);
            type.declaredInMemberId = declaredInMemberId;
            parent.addChild(type);
            allTypes.add(type);
            pkg.coverage.counter("typeDeclarations").addAttempt(selfRef.resolved, false);
            pkg.coverage.counter("localAndAnonymousTypes").addAttempt(selfRef.resolved, false);
            // anonymous supertype
            if (decl.getParent() instanceof ClassInstanceCreation cic && cic.getType() != null) {
                SymbolRef sr = refForType(cic.getType(), type, DependencyUsage.EXTENDS);
                // could be interface impl or class extend; record as extends edge for model simplicity
                type.superclass = sr;
                type.dependencyEdges.add(new DependencyEdge(type.symbolId, sr, DependencyUsage.EXTENDS));
            }
            @SuppressWarnings("unchecked") List<Object> bodies = decl.bodyDeclarations();
            Map<Method, MethodDeclaration> cohesionMethods = new LinkedHashMap<>();
            for (Object o : bodies) {
                if (o instanceof MethodDeclaration md) {
                    Member member = parseMethodLike(type, md);
                    if (member instanceof Method method) cohesionMethods.put(method, md);
                    collectLambdas(type, md, member.id);
                }
                else if (o instanceof FieldDeclaration fd) {
                    SymbolRef ft = refForType(fd.getType(), type, DependencyUsage.FIELD);
                    type.dependencyEdges.add(new DependencyEdge(type.symbolId, ft, DependencyUsage.FIELD));
                    for (Object f : fd.fragments()) if (f instanceof VariableDeclarationFragment frag) {
                        EnumSet<cz.utulnykoutek.metrics.domain.Modifier> fmods = toModifiers(fd.getModifiers());
                        String name = frag.getName().getIdentifier();
                        Field field = new Field(memberFallbackId(type, "field", name), null, type, name, fmods, ft, ft.resolved);
                        type.addMember(field);
                        if (frag.getInitializer() != null) {
                            collectLambdas(type, frag.getInitializer(), field.id);
                        }
                        pkg.coverage.counter("memberDeclarations").addAttempt(false, false);
                    }
                } else if (o instanceof Initializer initializer) {
                    parseInitializer(type, initializer);
                } else if (o instanceof AbstractTypeDeclaration nested) {
                    parseType(nested, type, path, null);
                }
            }
            type.cohesionEvidence = CohesionEvidenceExtractor.extract(type, cohesionMethods);
            collectBodyDependencies(type, bodies);
            // collect nested local/anon
            decl.accept(new ASTVisitor() {
                @Override public boolean visit(TypeDeclarationStatement node) { if (node.getDeclaration()!=null) parseLocalType(node.getDeclaration(), type, path, null); return false; }
                @Override public boolean visit(AnonymousClassDeclaration node) {
                    if (node == decl) return true;
                    parseAnonymous(node, type, path, null);
                    return false;
                }
            });
            rebuildDeps(type);
        }

        void collectBodyDependencies(TypeCompound type, List<?> bodyDeclarations) {
            for (Object declaration : bodyDeclarations) {
                if (declaration instanceof MethodDeclaration method && method.getBody() != null) {
                    collectBodyDependencies(type, method.getBody());
                } else if (declaration instanceof Initializer initializer) {
                    collectBodyDependencies(type, initializer.getBody());
                } else if (declaration instanceof FieldDeclaration field) {
                    for (Object fragment : field.fragments()) {
                        if (fragment instanceof VariableDeclarationFragment variable
                                && variable.getInitializer() != null) {
                            collectBodyDependencies(type, variable.getInitializer());
                        }
                    }
                } else if (declaration instanceof EnumConstantDeclaration constant) {
                    for (Object argument : constant.arguments()) {
                        if (argument instanceof Expression expression) {
                            collectBodyDependencies(type, expression);
                        }
                    }
                }
            }
        }

        void collectBodyDependencies(TypeCompound type, ASTNode root) {
            root.accept(new ASTVisitor() {
                @Override public boolean preVisit2(ASTNode node) {
                    if (node == root) return true;
                    return !(node instanceof AbstractTypeDeclaration)
                            && !(node instanceof AnonymousClassDeclaration);
                }

                @Override public boolean visit(VariableDeclarationStatement node) {
                    addBodyType(type, node.getType(), DependencyUsage.LOCAL_VARIABLE);
                    return true;
                }

                @Override public boolean visit(VariableDeclarationExpression node) {
                    addBodyType(type, node.getType(), DependencyUsage.LOCAL_VARIABLE);
                    return true;
                }

                @Override public boolean visit(ClassInstanceCreation node) {
                    addBodyType(type, node.getType(), DependencyUsage.INSTANTIATION);
                    addBodyMethod(type, node.resolveConstructorBinding(),
                            DependencyUsage.CONSTRUCTOR_CALL, node.toString());
                    return true;
                }

                @Override public boolean visit(ArrayCreation node) {
                    addBodyType(type, node.getType(), DependencyUsage.INSTANTIATION);
                    return true;
                }

                @Override public boolean visit(ConstructorInvocation node) {
                    addBodyMethod(type, node.resolveConstructorBinding(),
                            DependencyUsage.CONSTRUCTOR_CALL, node.toString());
                    return true;
                }

                @Override public boolean visit(SuperConstructorInvocation node) {
                    addBodyMethod(type, node.resolveConstructorBinding(),
                            DependencyUsage.CONSTRUCTOR_CALL, node.toString());
                    return true;
                }

                @Override public boolean visit(MethodInvocation node) {
                    addBodyMethod(type, node.resolveMethodBinding(),
                            DependencyUsage.METHOD_CALL, node.toString());
                    return true;
                }

                @Override public boolean visit(SuperMethodInvocation node) {
                    addBodyMethod(type, node.resolveMethodBinding(),
                            DependencyUsage.METHOD_CALL, node.toString());
                    return true;
                }

                @Override public boolean visit(SimpleName node) {
                    IBinding binding = node.resolveBinding();
                    if (binding instanceof IVariableBinding variable && variable.isField()) {
                        addBodyTypeBinding(type, variable.getDeclaringClass(),
                                DependencyUsage.FIELD_ACCESS, node.getIdentifier());
                    }
                    return true;
                }

                @Override public boolean visit(FieldAccess node) {
                    if (node.getName().resolveBinding() == null) {
                        addUnresolvedBodyDependency(type, DependencyUsage.FIELD_ACCESS,
                                node.toString());
                    }
                    return true;
                }

                @Override public boolean visit(CastExpression node) {
                    addBodyType(type, node.getType(), DependencyUsage.CAST);
                    return true;
                }

                @Override public boolean visit(InstanceofExpression node) {
                    if (node.getRightOperand() instanceof Type operand) {
                        addBodyType(type, operand, DependencyUsage.INSTANCEOF);
                    }
                    return true;
                }

                @Override public boolean visit(TypeLiteral node) {
                    addBodyType(type, node.getType(), DependencyUsage.CLASS_LITERAL);
                    return true;
                }

                @Override public boolean visit(ExpressionMethodReference node) {
                    addBodyMethod(type, node.resolveMethodBinding(),
                            DependencyUsage.METHOD_REFERENCE, node.toString());
                    return true;
                }

                @Override public boolean visit(TypeMethodReference node) {
                    addBodyMethod(type, node.resolveMethodBinding(),
                            DependencyUsage.METHOD_REFERENCE, node.toString());
                    return true;
                }

                @Override public boolean visit(SuperMethodReference node) {
                    addBodyMethod(type, node.resolveMethodBinding(),
                            DependencyUsage.METHOD_REFERENCE, node.toString());
                    return true;
                }

                @Override public boolean visit(CreationReference node) {
                    addBodyMethod(type, node.resolveMethodBinding(),
                            DependencyUsage.METHOD_REFERENCE, node.toString());
                    return true;
                }

                @Override public boolean visit(CatchClause node) {
                    addBodyType(type, node.getException().getType(), DependencyUsage.CATCH);
                    return true;
                }

                @Override public boolean visit(LambdaExpression node) {
                    addBodyTypeBinding(type, node.resolveTypeBinding(),
                            DependencyUsage.LAMBDA_TARGET, node.toString());
                    return true;
                }
            });
        }

        void addBodyType(TypeCompound owner, Type astType, DependencyUsage usage) {
            if (astType == null) return;
            ITypeBinding binding = astType.resolveBinding();
            if (binding == null) {
                addUnresolvedBodyDependency(owner, usage, astType.toString());
            } else {
                addBodyTypeBinding(owner, binding, usage, astType.toString());
            }
        }

        void addBodyMethod(TypeCompound owner, IMethodBinding binding,
                           DependencyUsage usage, String display) {
            if (binding == null || binding.getDeclaringClass() == null) {
                addUnresolvedBodyDependency(owner, usage, display);
            } else {
                addBodyTypeBinding(owner, binding.getDeclaringClass(), usage, display);
            }
        }

        void addBodyTypeBinding(TypeCompound owner, ITypeBinding binding,
                                DependencyUsage usage, String display) {
            if (binding == null) {
                addUnresolvedBodyDependency(owner, usage, display);
                return;
            }
            ITypeBinding normalized = binding;
            while (normalized.isArray()) normalized = normalized.getElementType();
            for (ITypeBinding argument : normalized.getTypeArguments()) {
                addBodyTypeBinding(owner, argument, usage, argument.getName());
            }
            owner.dependencyEdges.add(new DependencyEdge(
                    owner.symbolId, symbolFromBinding(normalized), usage));
        }

        void addUnresolvedBodyDependency(TypeCompound owner, DependencyUsage usage, String display) {
            String safeDisplay = display == null ? "?" : display;
            SymbolRef unresolved = new SymbolRef(
                    "tr:" + owner.symbolId + ":" + usage + ":" + norm(safeDisplay),
                    null, null, safeDisplay, Origin.UNRESOLVED, false, TypeKindHint.UNKNOWN);
            owner.dependencyEdges.add(new DependencyEdge(owner.symbolId, unresolved, usage));
            sample("bodyDependencies", owner.symbolId, safeDisplay, usage.name(), "MISSING_BINDING");
        }

        SourceSpan sourceSpan(ASTNode node) {
            int start = node.getStartPosition();
            if (node instanceof BodyDeclaration declaration && declaration.getJavadoc() != null) {
                @SuppressWarnings("unchecked")
                List<IExtendedModifier> modifiers = declaration.modifiers();
                if (!modifiers.isEmpty()) {
                    start = ((ASTNode) modifiers.getFirst()).getStartPosition();
                } else {
                    int afterJavadoc = declaration.getJavadoc().getStartPosition()
                            + declaration.getJavadoc().getLength();
                    while (afterJavadoc < source.length()
                            && Character.isWhitespace(source.charAt(afterJavadoc))) {
                        afterJavadoc++;
                    }
                    start = afterJavadoc;
                }
            }
            return new SourceSpan(Path.of(unitId), start, node.getStartPosition() + node.getLength());
        }

        void buildTypeRelations() {
            for (TypeCompound t : allTypes) {
                AbstractTypeDeclaration decl = null;
                for (var e : typeMap.entrySet()) if (e.getValue() == t) { decl = e.getKey(); break; }
                ITypeBinding tb = decl == null ? null : decl.resolveBinding();
                if (t.superclass != null) pkg.inheritanceEdges.add(new InheritanceEdge(t.symbolId, t.superclass));
                for (SymbolRef i : t.interfaces) pkg.implementationEdges.add(new ImplementationEdge(t.symbolId, i));
                for (DependencyEdge de : t.dependencyEdges) {
                    pkg.dependencyEdges.add(de);
                    pkg.coverage.counter("dependencyEdges").addAttempt(de.resolved, false);
                    pkg.coverage.counter("typeReferences").addAttempt(de.resolved, false);
                }
                // overrides
                if (tb != null) {
                    for (IMethodBinding mb : tb.getDeclaredMethods()) {
                        if (mb.isConstructor()) continue;
                        String overridingId = memberId(mb, t, "method", mb.getName(), null);
                        collectOverrides(t, overridingId, mb);
                    }
                } else {
                    // unresolved: still record that we couldn't determine overrides for methods
                    for (Member m : t.getMembers()) if (m.memberKind == MemberKind.METHOD) {
                        pkg.coverage.counter("overrideRelations").addAttempt(false, true);
                    }
                }
            }
        }

        void collectOverrides(TypeCompound owner, String overridingId, IMethodBinding mb) {
            Set<String> seen = new HashSet<>();
            ITypeBinding sup = mb.getDeclaringClass() == null ? null : mb.getDeclaringClass().getSuperclass();
            collectOverridesInHierarchy(overridingId, mb, sup, seen, "OVERRIDES_CLASS");
            if (mb.getDeclaringClass() != null) {
                for (ITypeBinding itf : allInterfaces(mb.getDeclaringClass(), new HashSet<>())) {
                    collectOverridesInHierarchy(overridingId, mb, itf, seen, "IMPLEMENTS_INTERFACE");
                }
            }
            // If none found, not an override - do not count as unresolved failure; counting attempts only when resolvable context? spec asks to track relations completeness.
        }

        void collectOverridesInHierarchy(String overridingId, IMethodBinding mb, ITypeBinding type, Set<String> seen, String kind) {
            if (type == null) return;
            for (IMethodBinding cand : type.getDeclaredMethods()) {
                if (mb.overrides(cand) || (mb.getName().equals(cand.getName()) && Arrays.equals(mb.getParameterTypes(), cand.getParameterTypes()))) {
                    String key = (cand.getKey() == null ? cand.getName() : cand.getKey());
                    if (!seen.add(key)) continue;
                    SymbolRef declType = symbolFromBinding(type);
                    String overriddenId = cand.getKey() == null ? null : "m:" + norm(cand.getKey());
                    pkg.overrideEdges.add(new OverrideEdge(overridingId, overriddenId, declType, cand.getKey() != null, kind));
                    pkg.coverage.counter("overrideRelations").addAttempt(cand.getKey() != null, false);
                }
            }
            collectOverridesInHierarchy(overridingId, mb, type.getSuperclass(), seen, "OVERRIDES_CLASS");
            for (ITypeBinding itf : type.getInterfaces()) collectOverridesInHierarchy(overridingId, mb, itf, seen, kind);
        }

        Set<ITypeBinding> allInterfaces(ITypeBinding t, Set<ITypeBinding> out) {
            if (t == null) return out;
            for (ITypeBinding itf : t.getInterfaces()) if (out.add(itf)) allInterfaces(itf, out);
            allInterfaces(t.getSuperclass(), out);
            return out;
        }

        void finalizeCoverage() { pkg.coverage.recomputeComplete(); }

        void sample(String category, String ownerId, String display, String usage, String reason) {
            if (pkg.coverage.unresolvedSamples.size() >= 50) return;
            pkg.coverage.unresolvedSamples.add(new UnresolvedSample(category, ownerId, display, usage, reason));
        }

        void rebuildDeps(TypeCompound t) { t.rebuildDependencySummary(); }

        SymbolRef selfSymbolRef(TypeCompound t) {
            return new SymbolRef(t.symbolId, t.bindingKey, t.qualifiedName, t.name, Origin.INTERNAL, t.resolved, hintFromKind(t.getKind()));
        }

        SymbolRef symbolForTypeBinding(ITypeBinding tb, AbstractTypeDeclaration decl, Compound parent, String path) {
            if (tb != null) return symbolFromBinding(tb);
            return new SymbolRef("ts:" + unitId + ":" + path, null, null, decl.getName() == null ? "" : decl.getName().getIdentifier(), Origin.INTERNAL, false, hintFromKind(kindOf(decl)));
        }

        SymbolRef symbolForAnonBinding(ITypeBinding tb, String path) {
            if (tb != null) return symbolFromBinding(tb);
            return new SymbolRef("ts:" + unitId + ":" + path, null, null, "<anonymous>", Origin.INTERNAL, false, TypeKindHint.CLASS);
        }

        SymbolRef symbolFromBinding(ITypeBinding tb) {
            if (tb == null) return SymbolRef.unresolved(null, "?");
            ITypeBinding er = tb.getErasure();
            String qn = er.getQualifiedName();
            if (qn == null || qn.isBlank()) qn = er.getName();
            Origin o = classifyOrigin(er, qn);
            String key = er.getKey();
            String id = key != null && !key.isBlank() ? "t:" + norm(key) : "tq:" + (qn == null ? er.getName() : qn);
            String disp = tb.getName() == null ? qn : tb.getName();
            String packageName = er.getPackage() == null ? null : er.getPackage().getName();
            boolean resolved = !er.isRecovered();
            return new SymbolRef(id, key, qn, packageName, disp,
                    resolved ? o : Origin.UNRESOLVED, resolved, hintFromBinding(er));
        }

        Origin classifyOrigin(ITypeBinding b, String qn) {
            if (b != null && (b.isPrimitive() || b.isArray() && b.getElementType() != null && b.getElementType().isPrimitive())) return Origin.JDK;
            if (qn == null) return Origin.EXTERNAL;
            if (qn.equals("void")) return Origin.JDK;
            if (qn.startsWith("java.") || qn.startsWith("javax.") || qn.startsWith("jdk.") || qn.startsWith("sun.") || qn.startsWith("com.sun.")) return Origin.JDK;
            return Origin.EXTERNAL;
        }

        SymbolRef refForType(Type t, TypeCompound owner, DependencyUsage usage) {
            ITypeBinding b = t == null ? null : t.resolveBinding();
            if (b == null) {
                String disp = t == null ? "?" : t.toString();
                pkg.coverage.counter("typeReferences").addAttempt(false, false);
                sample("typeReferences", owner.symbolId, disp, usage.name(), "MISSING_DEPENDENCY");
                return new SymbolRef("tr:" + owner.symbolId + ":" + usage + ":" + norm(disp), null, null, disp, Origin.UNRESOLVED, false, TypeKindHint.UNKNOWN);
            }
            SymbolRef sr = symbolFromBinding(b);
            pkg.coverage.counter("typeReferences").addAttempt(sr.resolved, false);
            return sr;
        }



        String qualifiedFromBinding(ITypeBinding tb, String pkgName, String path) {
            if (tb != null) {
                String qn = tb.getQualifiedName();
                if (qn != null && !qn.isBlank()) return qn;
            }
            return pkgName == null || pkgName.isEmpty() ? path : pkgName + "." + path;
        }

        String memberId(IMethodBinding mb, TypeCompound owner, String kind, String name, String sig) {
            if (mb != null && mb.getKey() != null) return "m:" + norm(mb.getKey());
            if (mb != null && mb.getMethodDeclaration() != null && mb.getMethodDeclaration().getKey() != null) return "m:" + norm(mb.getMethodDeclaration().getKey());
            return memberFallbackId(owner, kind, name + (sig == null ? "" : sig));
        }
        String memberId(IVariableBinding vb, TypeCompound owner, String kind, String name, String sig) {
            if (vb != null && vb.getKey() != null) return "m:" + norm(vb.getKey());
            return memberFallbackId(owner, kind, name);
        }
        String memberFallbackId(TypeCompound owner, String kind, String key) { return "ms:" + owner.symbolId + ":" + kind + ":" + norm(key); }

        String norm(String s) {
            if (s == null) return "";
            return s.replace('\\','/').replace('\n',' ').replace('\r',' ');
        }
    }

    private static String reason(ITypeBinding tb) { return tb == null ? "MISSING_DEPENDENCY" : "UNKNOWN"; }

    private static SymbolRef primitiveRef(String name) {
        return new SymbolRef("tp:" + name, null, name, name, Origin.JDK, true, TypeKindHint.PRIMITIVE);
    }

    private static TypeKindHint hintFromBinding(ITypeBinding b) {
        if (b == null) return TypeKindHint.UNKNOWN;
        if (b.isPrimitive()) return TypeKindHint.PRIMITIVE;
        if (b.isArray()) return TypeKindHint.ARRAY;
        if (b.isEnum()) return TypeKindHint.ENUM;
        if (b.isAnnotation()) return TypeKindHint.ANNOTATION;
        if (b.isRecord()) return TypeKindHint.RECORD;
        if (b.isInterface()) return TypeKindHint.INTERFACE;
        if (b.isTypeVariable()) return TypeKindHint.TYPE_PARAMETER;
        return TypeKindHint.CLASS;
    }

    private static TypeKindHint hintFromKind(CompoundKind k) {
        if (k == null) return TypeKindHint.UNKNOWN;
        return switch (k) {
            case CLASS -> TypeKindHint.CLASS;
            case INTERFACE -> TypeKindHint.INTERFACE;
            case ENUM -> TypeKindHint.ENUM;
            case RECORD -> TypeKindHint.RECORD;
            case ANNOTATION -> TypeKindHint.ANNOTATION;
            case PACKAGE -> TypeKindHint.UNKNOWN;
        };
    }

    private static NestingKind nestingOf(AbstractTypeDeclaration decl, Compound parent, EnumSet<cz.utulnykoutek.metrics.domain.Modifier> modifiers, CompoundKind kind) {
        if (parent instanceof PackageCompound) return NestingKind.TOP_LEVEL;
        // local/anon set explicitly by callers
        if (modifiers.contains(cz.utulnykoutek.metrics.domain.Modifier.STATIC) || kind == CompoundKind.INTERFACE || kind == CompoundKind.ENUM || kind == CompoundKind.RECORD || kind == CompoundKind.ANNOTATION) {
            return NestingKind.STATIC_MEMBER;
        }
        return NestingKind.INNER_MEMBER;
    }

    private static CompoundKind kindOf(AbstractTypeDeclaration decl) {
        if (decl instanceof EnumDeclaration) return CompoundKind.ENUM;
        if (decl instanceof AnnotationTypeDeclaration) return CompoundKind.ANNOTATION;
        if (decl instanceof RecordDeclaration) return CompoundKind.RECORD;
        if (decl instanceof TypeDeclaration td) return td.isInterface() ? CompoundKind.INTERFACE : CompoundKind.CLASS;
        return CompoundKind.CLASS;
    }

    private static EnumSet<cz.utulnykoutek.metrics.domain.Modifier> toModifiers(int mods) {
        EnumSet<cz.utulnykoutek.metrics.domain.Modifier> set = EnumSet.noneOf(cz.utulnykoutek.metrics.domain.Modifier.class);
        if (org.eclipse.jdt.core.dom.Modifier.isPublic(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.PUBLIC);
        if (org.eclipse.jdt.core.dom.Modifier.isProtected(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.PROTECTED);
        if (org.eclipse.jdt.core.dom.Modifier.isPrivate(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.PRIVATE);
        if (org.eclipse.jdt.core.dom.Modifier.isStatic(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.STATIC);
        if (org.eclipse.jdt.core.dom.Modifier.isFinal(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.FINAL);
        if (org.eclipse.jdt.core.dom.Modifier.isAbstract(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.ABSTRACT);
        if (org.eclipse.jdt.core.dom.Modifier.isStrictfp(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.STRICTFP);
        if (org.eclipse.jdt.core.dom.Modifier.isSynchronized(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.SYNCHRONIZED);
        if (org.eclipse.jdt.core.dom.Modifier.isVolatile(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.VOLATILE);
        if (org.eclipse.jdt.core.dom.Modifier.isTransient(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.TRANSIENT);
        if (org.eclipse.jdt.core.dom.Modifier.isNative(mods)) set.add(cz.utulnykoutek.metrics.domain.Modifier.NATIVE);
        return set;
    }
}
