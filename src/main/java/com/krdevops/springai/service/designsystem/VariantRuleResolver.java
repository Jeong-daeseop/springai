package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.VariantAxisDefinition;
import com.krdevops.springai.model.designsystem.VariantRule;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.ComponentResolutionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Context와 Rule Set으로 정확히 하나의 Published Variant를 계산한다. */
@Component
public class VariantRuleResolver {

    public Resolution resolve(
            ComponentRegistryEntry contract,
            ComponentResolutionContext context,
            VariantRuleSet ruleSet
    ) {
        if (contract == null || context == null || ruleSet == null || ruleSet.status() != VariantRuleSet.Status.PUBLISHED) {
            return failed("VARIANT_RULE_SET_NOT_PUBLISHED", List.of());
        }
        Map<String, String> contextValues = context.asRuleContext();
        List<VariantRule> matches = ruleSet.rules().stream()
                .filter(rule -> rule.role() == context.role())
                .filter(rule -> rule.when().entrySet().stream()
                        .allMatch(condition -> condition.getValue().equals(contextValues.get(condition.getKey()))))
                .toList();
        if (matches.isEmpty()) return failed("VARIANT_RULE_NOT_FOUND", matches);

        int highestSpecificity = matches.stream().mapToInt(rule -> rule.when().size()).max().orElse(0);
        List<VariantRule> specific = matches.stream().filter(rule -> rule.when().size() == highestSpecificity).toList();
        int highestPriority = specific.stream().mapToInt(VariantRule::priority).max().orElse(0);
        List<VariantRule> winners = specific.stream().filter(rule -> rule.priority() == highestPriority).toList();
        if (winners.size() != 1) return failed("VARIANT_RULE_AMBIGUOUS", winners);

        VariantRule rule = winners.get(0);
        Map<String, String> figmaProperties = new LinkedHashMap<>();
        for (Map.Entry<String, String> result : rule.result().entrySet()) {
            VariantAxisDefinition axis = contract.variantAxes().get(result.getKey());
            if (axis == null) return failed("VARIANT_AXIS_NOT_DECLARED", winners);
            String figmaValue = mappedValue(contract, result.getKey(), result.getValue());
            if (!containsIgnoreCase(axis.allowedValues(), figmaValue)) {
                return failed("VARIANT_VALUE_NOT_ALLOWED", winners);
            }
            figmaProperties.put(axis.figmaProperty(), figmaValue);
        }
        boolean missingRequired = contract.variantAxes().values().stream()
                .filter(VariantAxisDefinition::required)
                .anyMatch(axis -> !figmaProperties.containsKey(axis.figmaProperty()));
        if (missingRequired) return failed("REQUIRED_VARIANT_AXIS_MISSING", winners);

        List<Map.Entry<String, String>> variants = contract.variants().entrySet().stream()
                .filter(entry -> equalVariantValues(parseVariantName(entry.getKey()), figmaProperties))
                .toList();
        if (variants.isEmpty()) return failed("VARIANT_NOT_RESOLVED", winners);
        if (variants.size() > 1) return failed("VARIANT_AMBIGUOUS", winners);
        return new Resolution(true, null, rule.ruleId(), variants.get(0).getValue(),
                Map.copyOf(figmaProperties), List.copyOf(winners));
    }

    private String mappedValue(ComponentRegistryEntry contract, String logicalAxis, String logicalValue) {
        ComponentRegistryEntry.PropertyMapping mapping = contract.properties().get(logicalAxis);
        if (mapping == null || mapping.values() == null) return logicalValue;
        String exact = mapping.values().get(logicalValue);
        if (exact != null) return exact;
        // Rule Set은 계약 문서 관례상 Default/Focus처럼 표기될 수 있고,
        // Figma Registry는 default/focus처럼 저장될 수 있으므로 논리값은
        // 대소문자를 구분하지 않고 매핑한다.
        return mapping.values().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(logicalValue))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(logicalValue);
    }

    private boolean containsIgnoreCase(Iterable<String> values, String expected) {
        for (String value : values) {
            if (value.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    private boolean equalVariantValues(Map<String, String> expected, Map<String, String> actual) {
        if (expected.size() != actual.size()) return false;
        return expected.entrySet().stream().allMatch(entry -> actual.entrySet().stream()
                .anyMatch(candidate -> candidate.getKey().equalsIgnoreCase(entry.getKey())
                        && candidate.getValue().equalsIgnoreCase(entry.getValue())));
    }

    static Map<String, String> parseVariantName(String variantName) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : variantName.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2) values.put(pair[0].trim(), pair[1].trim());
        }
        return Map.copyOf(values);
    }

    private Resolution failed(String code, List<VariantRule> candidates) {
        return new Resolution(false, code, null, null, Map.of(), new ArrayList<>(candidates));
    }

    public record Resolution(
            boolean resolved,
            String errorCode,
            String ruleId,
            String variantKey,
            Map<String, String> variantProperties,
            List<VariantRule> candidates
    ) {}
}
