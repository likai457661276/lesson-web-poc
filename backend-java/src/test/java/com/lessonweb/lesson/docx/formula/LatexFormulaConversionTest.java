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
    void preservesOverbarsUnderbarsAndAccentsAsStructures() {
        String bars = omml.convert(latex.convert("\\overline{PQ}+\\underline{uv}"));
        assertThat(bars).contains("<m:bar>", "m:val=\"top\"", "m:val=\"bot\"");
        var xml = org.jsoup.Jsoup.parse(bars, "", org.jsoup.parser.Parser.xmlParser());
        assertThat(xml.getElementsByTag("m:bar")).hasSize(2);
        assertThat(xml.getElementsByTag("m:bar").get(0).getElementsByTag("m:t").eachText()).containsExactly("P", "Q");
        assertThat(xml.getElementsByTag("m:bar").get(1).getElementsByTag("m:t").eachText()).containsExactly("u", "v");
        assertThat(omml.convert(latex.convert("\\hat{z}+\\vec{v}"))).contains("<m:acc>", "<m:chr");
    }

    @Test
    void rejectsUnknownMathmlInsteadOfFlatteningItsChildren() {
        for (String structure : new String[]{"<mtable><mtr><mtd><mi>x</mi></mtd></mtr></mtable>",
                "<mphantom><mi>y</mi></mphantom>", "<munder><mi>lim</mi><mi>x</mi></munder>"}) {
            assertThatThrownBy(() -> omml.convert("<math xmlns='http://www.w3.org/1998/Math/MathML'>" + structure + "</math>"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void ignoresFormattingWhitespaceBetweenOperandsAndNonPresentationAnnotations() {
        String output = omml.convert("<math xmlns='http://www.w3.org/1998/Math/MathML'><semantics><mfrac>\n<mi>a</mi>\n<mi>b</mi>\n</mfrac>"
                + "<annotation encoding='application/x-tex'>a/b</annotation></semantics></math>");
        assertThat(output).contains("<m:num><m:r>", "<m:den><m:r>").doesNotContain("a/b");
    }

    @Test
    void rejectsUnsupportedCommands() {
        assertThatThrownBy(() -> latex.convert("\\unknown{x}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
