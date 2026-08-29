package com.lessonweb.lesson.model.formula;

public record FormulaValidationResult(
        String latex,
        String normalizedLatex,
        boolean parseable,
        String symbolicExpression,
        Boolean equivalentToReference,
        String message
) {
}
