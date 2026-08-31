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
        assertThat(output).contains("m:sSubSup", "m:f", "m:rad", "m:deg/", "≥", "∘");
    }

    @Test
    void convertsMineruTableFormulaCommandsWithMathStyles() {
        String output = omml.convert(latex.convert("\\left(5 \\div 4 = 1 \\cdots 1 \\rightarrow 2\\right)+\\sin\\theta"));

        assertThat(output).contains("m:d", "÷", "⋯", "→", "m:sty m:val=\"p\"", "m:sty m:val=\"i\"");
        assertThat(output).doesNotContain("\\div", "\\cdots", "\\rightarrow");
    }

    @Test
    void preservesChineseAtomsAndTextBoxesInEditableMath() {
        for (String formula : new String[]{"A_{底}=2\\pi r", "V_{\\text{总体}}=a^3", "\\text{面积}+b_{甲}"}) {
            String output = omml.convert(latex.convert(formula));
            assertThat(output).contains("m:oMath", "m:sSub");
            assertThat(output).doesNotContain("lessonhan", "mbox", "<m:t>text</m:t>");
        }
        assertThat(omml.convert(latex.convert("A_{底}=2\\pi r"))).contains("底", "π");
        assertThat(omml.convert(latex.convert("V_{\\text{总体}}=a^3"))).contains("总", "体");
    }

    @Test
    void rejectsUnsupportedCommands() {
        assertThatThrownBy(() -> latex.convert("\\unknown{x}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
