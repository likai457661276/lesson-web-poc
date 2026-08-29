package com.lessonweb.lesson.docx.formula;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatexFormulaConversionTest {

    private final LatexToMathmlConverter latex = new LatexToMathmlConverter();
    private final MathmlToOmmlConverter omml = new MathmlToOmmlConverter();

    @Test
    void convertsBusinessFormulaSetToEditableOmml() {
        String output = omml.convert(latex.convert("x_1^2+\\frac{a}{b}\\geq\\sqrt{y}+90^\\circ"));
        assertThat(output).contains("m:sSubSup", "m:f", "m:rad", "m:deg/", "≥", "°");
    }

    @Test
    void rejectsUnsupportedCommands() {
        assertThatThrownBy(() -> latex.convert("\\unknown{x}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
