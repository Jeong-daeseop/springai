package com.krdevops.springai.service.thymeleaf;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.krdevops.springai.model.thymeleaf.VoEvidence;
import com.krdevops.springai.model.thymeleaf.VoFieldEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * I-2C: VO 소스를 JavaParser AST로 읽어 필드·타입·Bean Validation·접근자 증거를 추출한다.
 * eGovFrame VO는 Lombok {@code @Getter}/{@code @Setter}/{@code @Data}로 접근자를 생성하는 경우가
 * 많아, 실제 메서드가 소스에 없어도 클래스·필드 레벨 Lombok 애노테이션으로 readable/writable을
 * 판정한다.
 */
@Component
public class VoSourceReader {

    private static final Set<String> VALIDATION_ANNOTATION_NAMES = Set.of(
            "NotNull", "NotBlank", "NotEmpty", "Size", "Pattern", "Email",
            "Min", "Max", "Digits", "Positive", "PositiveOrZero", "Negative", "NegativeOrZero",
            "Past", "PastOrPresent", "Future", "FutureOrPresent", "AssertTrue", "AssertFalse",
            "DecimalMin", "DecimalMax");

    public VoEvidence read(String voPath, String content) {
        if (voPath == null || voPath.isBlank()) {
            throw new IllegalArgumentException("voPath는 필수입니다.");
        }
        CompilationUnit unit = StaticJavaParser.parse(content);
        ClassOrInterfaceDeclaration clazz = unit.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalArgumentException("VO_CLASS_NOT_FOUND: " + voPath));

        boolean classGetter = hasLombokAccessor(clazz.getAnnotations(), "Getter")
                || hasLombokAccessor(clazz.getAnnotations(), "Data")
                || hasLombokAccessor(clazz.getAnnotations(), "Value");
        boolean classSetter = hasLombokAccessor(clazz.getAnnotations(), "Setter")
                || hasLombokAccessor(clazz.getAnnotations(), "Data");

        Set<String> explicitAccessorMethods = clazz.getMethods().stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(Collectors.toSet());

        List<VoFieldEvidence> fields = new ArrayList<>();
        for (FieldDeclaration field : clazz.getFields()) {
            if (field.isStatic()) {
                continue;
            }
            boolean fieldGetter = classGetter || hasLombokAccessor(field.getAnnotations(), "Getter");
            boolean fieldSetter = classSetter || hasLombokAccessor(field.getAnnotations(), "Setter");
            List<String> validations = field.getAnnotations().stream()
                    .filter(annotation -> VALIDATION_ANNOTATION_NAMES.contains(annotation.getNameAsString()))
                    .map(AnnotationExpr::toString)
                    .toList();

            for (VariableDeclarator variable : field.getVariables()) {
                String fieldName = variable.getNameAsString();
                String type = variable.getTypeAsString();
                boolean readable = fieldGetter || explicitAccessorMethods.contains(getterName(fieldName, type));
                boolean writable = fieldSetter || explicitAccessorMethods.contains(setterName(fieldName));
                fields.add(new VoFieldEvidence(
                        fieldName, type, readable, writable, validations, voPath + "#" + fieldName));
            }
        }

        return new VoEvidence(voPath, clazz.getNameAsString(), fields);
    }

    private boolean hasLombokAccessor(java.util.List<AnnotationExpr> annotations, String annotationName) {
        return annotations.stream().anyMatch(annotation -> annotation.getNameAsString().equals(annotationName));
    }

    private String getterName(String fieldName, String type) {
        String capitalized = capitalize(fieldName);
        return ("boolean".equals(type) || "Boolean".equals(type)) ? "is" + capitalized : "get" + capitalized;
    }

    private String setterName(String fieldName) {
        return "set" + capitalize(fieldName);
    }

    private String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
