package com.lessonweb.lesson.model.formula;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FormulaValidationRequest(
        @NotNull @Size(min = 1, max = 4000) String latex,
        @Size(max = 4000) String referenceLatex
) {
}
