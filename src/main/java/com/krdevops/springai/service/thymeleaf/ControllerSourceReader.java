package com.krdevops.springai.service.thymeleaf;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.krdevops.springai.model.thymeleaf.ControllerEvidence;
import com.krdevops.springai.model.thymeleaf.ControllerMethodEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * I-2C: Controller 소스를 JavaParser AST로 읽어 매핑·모델 바인딩·반환뷰·보안 증거를 추출한다.
 * 이 저장소의 다른 Source Reader(정규식 기반)와 달리, 정확한 애노테이션·파라미터 해석이
 * FATAL 판정에 직결되므로 AST 파서를 사용한다.
 */
@Component
public class ControllerSourceReader {

    private static final Set<String> SECURITY_ANNOTATION_NAMES = Set.of(
            "PreAuthorize", "PostAuthorize", "Secured", "RolesAllowed");
    private static final Set<String> NON_MODEL_PARAMETER_TYPES = Set.of(
            "String", "int", "Integer", "long", "Long", "boolean", "Boolean",
            "ModelMap", "Model", "ModelAndView", "BindingResult", "Errors",
            "RedirectAttributes", "HttpServletRequest", "HttpServletResponse", "HttpSession", "Locale");

    public ControllerEvidence read(String controllerPath, String content) {
        if (controllerPath == null || controllerPath.isBlank()) {
            throw new IllegalArgumentException("controllerPath는 필수입니다.");
        }
        CompilationUnit unit = StaticJavaParser.parse(content);
        ClassOrInterfaceDeclaration clazz = unit.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalArgumentException("CONTROLLER_CLASS_NOT_FOUND: " + controllerPath));

        String classLevelMapping = classLevelMappingPath(clazz.getAnnotations()).orElse(null);
        List<String> classSecurityEvidence = securityEvidence(clazz.getAnnotations());

        List<ControllerMethodEvidence> methods = new ArrayList<>();
        for (MethodDeclaration method : clazz.getMethods()) {
            readMethod(method, clazz, classLevelMapping, classSecurityEvidence, controllerPath)
                    .ifPresent(methods::add);
        }

        return new ControllerEvidence(controllerPath, clazz.getNameAsString(), classLevelMapping, methods);
    }

    private Optional<ControllerMethodEvidence> readMethod(
            MethodDeclaration method,
            ClassOrInterfaceDeclaration clazz,
            String classLevelMapping,
            List<String> classSecurityEvidence,
            String controllerPath
    ) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String httpMethod = mappingHttpMethod(annotation);
            if (httpMethod == null) {
                continue;
            }
            String route = combineRoute(classLevelMapping, mappingPath(annotation));

            String modelAttributeParamName = null;
            String modelAttributeType = null;
            boolean validated = false;
            for (Parameter parameter : method.getParameters()) {
                Optional<String> explicitModelAttribute = parameterAnnotationValue(parameter, "ModelAttribute");
                boolean hasModelAttributeAnnotation = parameter.getAnnotations().stream()
                        .anyMatch(a -> a.getNameAsString().equals("ModelAttribute"));
                boolean impliedModelAttribute = !hasAnyBindingAnnotation(parameter)
                        && !NON_MODEL_PARAMETER_TYPES.contains(parameter.getTypeAsString())
                        && parameter.getTypeAsString().endsWith("VO");
                if (hasModelAttributeAnnotation || impliedModelAttribute) {
                    modelAttributeParamName = explicitModelAttribute.orElseGet(
                            () -> decapitalize(parameter.getTypeAsString()));
                    modelAttributeType = parameter.getTypeAsString();
                    validated = parameter.getAnnotations().stream()
                            .anyMatch(a -> a.getNameAsString().equals("Valid") || a.getNameAsString().equals("Validated"));
                }
            }

            List<String> securityEvidence = new ArrayList<>(classSecurityEvidence);
            securityEvidence.addAll(securityEvidence(method.getAnnotations()));

            List<String> modelAttributesAdded = collectAddedModelAttributes(
                    method, clazz, new LinkedHashSet<>());
            String returnLiteral = resolveReturnLiteral(method);
            boolean redirect = returnLiteral != null && returnLiteral.startsWith("redirect:");

            return Optional.of(new ControllerMethodEvidence(
                    method.getNameAsString(), httpMethod, route,
                    modelAttributeParamName, modelAttributeType, validated,
                    modelAttributesAdded, returnLiteral, redirect, securityEvidence,
                    controllerPath + "#" + method.getNameAsString()));
        }
        return Optional.empty();
    }

    private boolean hasAnyBindingAnnotation(Parameter parameter) {
        return parameter.getAnnotations().stream().anyMatch(a -> Set.of(
                "ModelAttribute", "RequestParam", "PathVariable", "RequestBody", "RequestHeader", "CookieValue")
                .contains(a.getNameAsString()));
    }

    private String mappingHttpMethod(AnnotationExpr annotation) {
        return switch (annotation.getNameAsString()) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            case "RequestMapping" -> annotationEnumMember(annotation, "method").orElse("GET");
            default -> null;
        };
    }

    private Optional<String> classLevelMappingPath(java.util.List<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            if (annotation.getNameAsString().equals("RequestMapping")) {
                Optional<String> path = mappingPath(annotation);
                if (path.isPresent()) {
                    return path;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> mappingPath(AnnotationExpr annotation) {
        Optional<String> value = annotationStringMember(annotation, "value");
        if (value.isPresent()) {
            return value;
        }
        return annotationStringMember(annotation, "path");
    }

    private String combineRoute(String classLevelMapping, Optional<String> methodLevelPath) {
        String methodPath = methodLevelPath.orElse("");
        if (classLevelMapping == null || classLevelMapping.isBlank()) {
            return methodPath;
        }
        if (methodPath.isBlank()) {
            return classLevelMapping;
        }
        String prefix = classLevelMapping.endsWith("/")
                ? classLevelMapping.substring(0, classLevelMapping.length() - 1) : classLevelMapping;
        String suffix = methodPath.startsWith("/") ? methodPath : "/" + methodPath;
        return prefix + suffix;
    }

    private List<String> securityEvidence(java.util.List<AnnotationExpr> annotations) {
        List<String> evidence = new ArrayList<>();
        for (AnnotationExpr annotation : annotations) {
            if (SECURITY_ANNOTATION_NAMES.contains(annotation.getNameAsString())) {
                evidence.add(annotation.toString());
            }
        }
        return evidence;
    }

    private Optional<String> parameterAnnotationValue(Parameter parameter, String annotationName) {
        for (AnnotationExpr annotation : parameter.getAnnotations()) {
            if (annotation.getNameAsString().equals(annotationName)) {
                return annotationStringMember(annotation, "value");
            }
        }
        return Optional.empty();
    }

    /**
     * {@code model.addAttribute(...)}뿐 아니라, 같은 클래스의 scope 없는 helper 메서드 호출
     * (예: {@code populateLayoutModel(model, ...)})까지 한 단계 따라가며 수집한다.
     * {@code visitedMethods}는 helper끼리 서로 호출하는 순환을 막는 방문 기록이다.
     */
    private List<String> collectAddedModelAttributes(
            MethodDeclaration method, ClassOrInterfaceDeclaration clazz, Set<String> visitedMethods) {
        if (!visitedMethods.add(method.getSignature().asString())) {
            return List.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            String callName = call.getNameAsString();
            if (callName.equals("addAttribute") || callName.equals("addFlashAttribute")) {
                if (!call.getArguments().isEmpty()) {
                    literalString(call.getArgument(0)).ifPresent(keys::add);
                }
                continue;
            }
            if (call.getScope().isPresent()) {
                continue;
            }
            clazz.getMethods().stream()
                    .filter(candidate -> candidate.getNameAsString().equals(call.getNameAsString()))
                    .findFirst()
                    .ifPresent(helper -> keys.addAll(collectAddedModelAttributes(helper, clazz, visitedMethods)));
        }
        return List.copyOf(keys);
    }

    private String resolveReturnLiteral(MethodDeclaration method) {
        List<ReturnStmt> returns = method.findAll(ReturnStmt.class);
        for (int i = returns.size() - 1; i >= 0; i--) {
            Optional<Expression> expression = returns.get(i).getExpression();
            if (expression.isEmpty()) {
                continue;
            }
            Optional<String> resolved = resolveStringExpression(expression.get(), method);
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        return null;
    }

    private Optional<String> resolveStringExpression(Expression expression, MethodDeclaration method) {
        Optional<String> literal = literalString(expression);
        if (literal.isPresent()) {
            return literal;
        }
        if (expression.isMethodCallExpr()) {
            MethodCallExpr call = expression.asMethodCallExpr();
            if (call.getScope().isPresent() && call.getScope().get().isNameExpr()) {
                String variableName = call.getScope().get().asNameExpr().getNameAsString();
                return traceStringBuilderPrefix(method, variableName);
            }
        }
        return Optional.empty();
    }

    private Optional<String> traceStringBuilderPrefix(MethodDeclaration method, String variableName) {
        return method.findAll(VariableDeclarator.class).stream()
                .filter(declarator -> declarator.getNameAsString().equals(variableName))
                .flatMap(declarator -> declarator.getInitializer().stream())
                .filter(Expression::isObjectCreationExpr)
                .map(Expression::asObjectCreationExpr)
                .filter(creation -> !creation.getArguments().isEmpty())
                .flatMap(creation -> literalString(creation.getArgument(0)).stream())
                .findFirst();
    }

    private Optional<String> literalString(Expression expression) {
        if (expression.isStringLiteralExpr()) {
            return Optional.of(expression.asStringLiteralExpr().asString());
        }
        if (expression.isArrayInitializerExpr()) {
            ArrayInitializerExpr array = expression.asArrayInitializerExpr();
            return array.getValues().isEmpty() ? Optional.empty() : literalString(array.getValues().get(0));
        }
        return Optional.empty();
    }

    private Optional<String> annotationStringMember(AnnotationExpr annotation, String memberName) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return memberName.equals("value")
                    ? literalString(annotation.asSingleMemberAnnotationExpr().getMemberValue())
                    : Optional.empty();
        }
        if (annotation.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals(memberName)) {
                    return literalString(pair.getValue());
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> annotationEnumMember(AnnotationExpr annotation, String memberName) {
        if (!annotation.isNormalAnnotationExpr()) {
            return Optional.empty();
        }
        for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
            if (!pair.getNameAsString().equals(memberName)) {
                continue;
            }
            Expression value = pair.getValue();
            if (value.isFieldAccessExpr()) {
                return Optional.of(value.asFieldAccessExpr().getNameAsString());
            }
            if (value.isArrayInitializerExpr() && !value.asArrayInitializerExpr().getValues().isEmpty()) {
                Expression first = value.asArrayInitializerExpr().getValues().get(0);
                if (first.isFieldAccessExpr()) {
                    return Optional.of(first.asFieldAccessExpr().getNameAsString());
                }
            }
        }
        return Optional.empty();
    }

    private String decapitalize(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return typeName;
        }
        return Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
    }
}
