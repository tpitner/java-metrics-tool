// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.parser;

import cz.utulnykoutek.metrics.domain.*;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/** Extracts the source evidence needed by the primary LCOM4 definition. */
final class CohesionEvidenceExtractor {
    private CohesionEvidenceExtractor() {}

    static TypeCohesionEvidence extract(
            TypeCompound owner,
            Map<cz.utulnykoutek.metrics.domain.Method, MethodDeclaration> declarations) {
        Set<Field> instanceFields = new LinkedHashSet<>();
        Map<String, Field> fieldsByBinding = new LinkedHashMap<>();
        Map<String, List<Field>> fieldsByName = new LinkedHashMap<>();
        owner.getMembers().stream().filter(Field.class::isInstance).map(Field.class::cast)
                .filter(field -> !field.modifiers.contains(
                        cz.utulnykoutek.metrics.domain.Modifier.STATIC))
                .forEach(field -> {
                    instanceFields.add(field);
                    if (field.bindingKey != null) fieldsByBinding.put(field.bindingKey, field);
                    fieldsByName.computeIfAbsent(field.name, ignored -> new ArrayList<>()).add(field);
                });

        Map<String, cz.utulnykoutek.metrics.domain.Method> methodsByBinding = new LinkedHashMap<>();
        Map<MethodSignature, List<cz.utulnykoutek.metrics.domain.Method>> methodsBySignature =
                new LinkedHashMap<>();
        declarations.keySet().forEach(method -> {
            if (method.bindingKey != null) methodsByBinding.put(method.bindingKey, method);
            methodsBySignature.computeIfAbsent(
                    new MethodSignature(method.name, method.parameters.size()),
                    ignored -> new ArrayList<>()).add(method);
        });

        List<MethodCohesionEvidence> evidence = new ArrayList<>();
        declarations.forEach((method, declaration) -> evidence.add(extractMethod(
                method, declaration, owner.bindingKey, fieldsByBinding, fieldsByName,
                methodsByBinding, methodsBySignature)));
        return new TypeCohesionEvidence(owner, instanceFields, evidence);
    }

    private static MethodCohesionEvidence extractMethod(
            cz.utulnykoutek.metrics.domain.Method method,
            MethodDeclaration declaration,
            String ownerBindingKey,
            Map<String, Field> fieldsByBinding,
            Map<String, List<Field>> fieldsByName,
            Map<String, cz.utulnykoutek.metrics.domain.Method> methodsByBinding,
            Map<MethodSignature, List<cz.utulnykoutek.metrics.domain.Method>> methodsBySignature) {
        EnumSet<CohesionMethodExclusion> exclusions = EnumSet.noneOf(CohesionMethodExclusion.class);
        if (declaration.getBody() == null) exclusions.add(CohesionMethodExclusion.NO_BODY);
        if (org.eclipse.jdt.core.dom.Modifier.isStatic(declaration.getModifiers())) {
            exclusions.add(CohesionMethodExclusion.STATIC_METHOD);
        }

        EvidenceVisitor visitor = new EvidenceVisitor(declaration, ownerBindingKey, fieldsByBinding,
                fieldsByName, methodsByBinding, methodsBySignature);
        if (declaration.getBody() != null) declaration.getBody().accept(visitor);
        if (isTrivialAccessor(declaration, visitor)) {
            exclusions.add(CohesionMethodExclusion.TRIVIAL_ACCESSOR);
        }
        return new MethodCohesionEvidence(method, exclusions,
                visitor.accessedFields, visitor.calledMethods,
                visitor.unresolvedFieldAccessCount, visitor.unresolvedIntraTypeCallCount);
    }

    private static boolean isTrivialAccessor(MethodDeclaration declaration, EvidenceVisitor visitor) {
        Block body = declaration.getBody();
        if (body == null || body.statements().size() != 1
                || visitor.accessedFields.size() != 1
                || !visitor.calledMethods.isEmpty()
                || visitor.unresolvedFieldAccessCount != 0
                || visitor.unresolvedIntraTypeCallCount != 0) return false;

        Object statement = body.statements().getFirst();
        if (declaration.parameters().isEmpty() && statement instanceof ReturnStatement returned) {
            return visitor.isDirectFieldExpression(unparenthesized(returned.getExpression()));
        }
        if (declaration.parameters().size() == 1
                && statement instanceof ExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof Assignment assignment
                && assignment.getOperator() == Assignment.Operator.ASSIGN
                && visitor.isDirectFieldExpression(unparenthesized(assignment.getLeftHandSide()))
                && unparenthesized(assignment.getRightHandSide()) instanceof SimpleName right) {
            IBinding binding = right.resolveBinding();
            return binding instanceof IVariableBinding variable && variable.isParameter();
        }
        return false;
    }

    private static Expression unparenthesized(Expression expression) {
        Expression result = expression;
        while (result instanceof ParenthesizedExpression parenthesized) {
            result = parenthesized.getExpression();
        }
        return result;
    }

    private record MethodSignature(String name, int arity) {}

    private static final class EvidenceVisitor extends ASTVisitor {
        private final ASTNode root;
        private final String ownerBindingKey;
        private final Map<String, Field> fieldsByBinding;
        private final Map<String, List<Field>> fieldsByName;
        private final Map<String, cz.utulnykoutek.metrics.domain.Method> methodsByBinding;
        private final Map<MethodSignature, List<cz.utulnykoutek.metrics.domain.Method>> methodsBySignature;
        private final Set<Field> accessedFields = new LinkedHashSet<>();
        private final Set<cz.utulnykoutek.metrics.domain.Method> calledMethods = new LinkedHashSet<>();
        private int unresolvedFieldAccessCount;
        private int unresolvedIntraTypeCallCount;

        private EvidenceVisitor(
                ASTNode root,
                String ownerBindingKey,
                Map<String, Field> fieldsByBinding,
                Map<String, List<Field>> fieldsByName,
                Map<String, cz.utulnykoutek.metrics.domain.Method> methodsByBinding,
                Map<MethodSignature, List<cz.utulnykoutek.metrics.domain.Method>> methodsBySignature) {
            this.root = root;
            this.ownerBindingKey = ownerBindingKey;
            this.fieldsByBinding = fieldsByBinding;
            this.fieldsByName = fieldsByName;
            this.methodsByBinding = methodsByBinding;
            this.methodsBySignature = methodsBySignature;
        }

        @Override public boolean preVisit2(ASTNode node) {
            if (node == root) return true;
            return !(node instanceof AbstractTypeDeclaration)
                    && !(node instanceof AnonymousClassDeclaration);
        }

        @Override public boolean visit(SimpleName node) {
            IBinding binding = node.resolveBinding();
            if (binding instanceof IVariableBinding variable && variable.isField()) {
                Field field = fieldFor(variable);
                if (field != null) accessedFields.add(field);
            } else if (binding == null && !node.isDeclaration()
                    && fieldsByName.containsKey(node.getIdentifier()) && isExpressionName(node)) {
                unresolvedFieldAccessCount++;
            }
            return true;
        }

        @Override public boolean visit(MethodInvocation node) {
            IMethodBinding binding = node.resolveMethodBinding();
            if (binding != null) {
                cz.utulnykoutek.metrics.domain.Method method =
                        methodsByBinding.get(binding.getMethodDeclaration().getKey());
                if (method != null) calledMethods.add(method);
            } else if (node.getExpression() == null
                    || node.getExpression() instanceof ThisExpression) {
                List<cz.utulnykoutek.metrics.domain.Method> candidates = methodsBySignature.get(
                        new MethodSignature(node.getName().getIdentifier(), node.arguments().size()));
                if (candidates != null && !candidates.isEmpty()) unresolvedIntraTypeCallCount++;
            }
            return true;
        }

        private Field fieldFor(Expression expression) {
            if (expression instanceof SimpleName simpleName
                    && simpleName.resolveBinding() instanceof IVariableBinding variable) {
                return fieldFor(variable);
            }
            if (expression instanceof FieldAccess access
                    && access.getName().resolveBinding() instanceof IVariableBinding variable) {
                return fieldFor(variable);
            }
            if (expression instanceof QualifiedName qualified
                    && qualified.getName().resolveBinding() instanceof IVariableBinding variable) {
                return fieldFor(variable);
            }
            return null;
        }

        private boolean isDirectFieldExpression(Expression expression) {
            if (fieldFor(expression) != null) return true;
            String name = switch (expression) {
                case SimpleName simple -> simple.getIdentifier();
                case FieldAccess access -> access.getName().getIdentifier();
                case QualifiedName qualified -> qualified.getName().getIdentifier();
                default -> null;
            };
            return name != null && accessedFields.size() == 1
                    && accessedFields.iterator().next().name.equals(name);
        }

        private Field fieldFor(IVariableBinding binding) {
            if (!binding.isField()
                    || org.eclipse.jdt.core.dom.Modifier.isStatic(binding.getModifiers())) return null;
            Field byBinding = fieldsByBinding.get(binding.getVariableDeclaration().getKey());
            if (byBinding != null) return byBinding;
            ITypeBinding declaringClass = binding.getDeclaringClass();
            if (declaringClass == null || ownerBindingKey == null
                    || !ownerBindingKey.equals(declaringClass.getTypeDeclaration().getKey())) return null;
            List<Field> sameName = fieldsByName.get(binding.getName());
            return sameName != null && sameName.size() == 1 ? sameName.getFirst() : null;
        }

        private static boolean isExpressionName(SimpleName node) {
            ASTNode parent = node.getParent();
            if (parent instanceof MethodInvocation invocation && invocation.getName() == node) return false;
            return !(parent instanceof LambdaExpression);
        }
    }
}
