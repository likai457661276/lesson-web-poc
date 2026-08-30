package com.lessonweb.lesson.service;

import com.lessonweb.lesson.model.formula.FormulaValidationRequest;
import com.lessonweb.lesson.model.formula.FormulaValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormulaValidationServiceTest {

    private final FormulaValidationService service = new FormulaValidationService();

    @Test
    void normalizesExpectedCharacters() {
        assertThat(service.normalizeLatex(" $x−1≤90°$ ")).isEqualTo("x-1\\leq 90^{\\circ}");
    }

    @Test
    void parsesCoreBusinessFormulaSet() {
        for (String formula : new String[]{
                "x^2 + 2 x + 1", "\\frac{x+1}{2}", "\\sqrt{x}",
                "\\sin(x)^2 + \\cos(x)^2", "0 \\leq x \\leq 1", "x=1, y=2"
        }) {
            FormulaValidationResult result = service.validate(new FormulaValidationRequest(formula, null));
            assertThat(result.parseable()).as(formula).isTrue();
            assertThat(result.symbolicExpression()).isNotBlank();
        }
    }

    @Test
    void detectsSymbolicEquivalence() {
        assertThat(service.validate(new FormulaValidationRequest("(x+1)^2", "x^2+2x+1"))
                .equivalentToReference()).isTrue();
        assertThat(service.validate(new FormulaValidationRequest("\\frac{2x}{2}", "x"))
                .equivalentToReference()).isTrue();
        assertThat(service.validate(new FormulaValidationRequest("x+1", "x+2"))
                .equivalentToReference()).isFalse();
    }

    @Test
    void preservesUnparseableInputAsValidationResult() {
        FormulaValidationResult result = service.validate(new FormulaValidationRequest("\\frac{", null));
        assertThat(result.parseable()).isFalse();
        assertThat(result.symbolicExpression()).isNull();
        assertThat(result.message()).startsWith("Symja 无法解析该 LaTeX：");
    }

    @Test
    void matchesGoldenReferenceMatrix() {
        GoldenCase[] cases = {
                new GoldenCase("x^2+2x+1", null, true, null),
                new GoldenCase("\\frac{x+1}{2}", null, true, null),
                new GoldenCase("\\sqrt{x}", null, true, null),
                new GoldenCase("\\sin(x)^2+\\cos(x)^2", null, true, null),
                new GoldenCase("0 \\leq x \\leq 1", null, true, null),
                new GoldenCase("x=1, y=2", null, true, null),
                new GoldenCase("(x+1)^2", "x^2+2x+1", true, true),
                new GoldenCase("\\frac{2x}{2}", "x", true, true),
                new GoldenCase("x+1", "x+2", true, false),
                new GoldenCase("x^3-x", "x*(x-1)*(x+1)", true, true),
                new GoldenCase("\\frac{1}{x}", "x^{-1}", true, true),
                new GoldenCase("\\sqrt{x^2}", "x", true, false),
                new GoldenCase("\\tan(x)", null, true, null),
                new GoldenCase("e^x", null, true, null),
                new GoldenCase("\\log(x)", null, true, null),
                new GoldenCase("a<b<c", null, true, null),
                new GoldenCase("90°", null, true, null),
                new GoldenCase("$x+1$", null, true, null),
                new GoldenCase("\\frac{", null, false, null),
                new GoldenCase("(x+1", null, false, null)
        };
        java.util.List<String> mismatches = new java.util.ArrayList<>();
        for (GoldenCase golden : cases) {
            FormulaValidationResult actual = service.validate(
                    new FormulaValidationRequest(golden.latex, golden.reference));
            if (actual.parseable() == golden.parseable
                    && java.util.Objects.equals(actual.equivalentToReference(), golden.equivalent)) {
                continue;
            }
            mismatches.add(golden.latex + " -> parseable=" + actual.parseable()
                    + ", equivalent=" + actual.equivalentToReference());
        }
        assertThat(mismatches).isEmpty();
    }

    private record GoldenCase(String latex, String reference, boolean parseable, Boolean equivalent) {}
}
