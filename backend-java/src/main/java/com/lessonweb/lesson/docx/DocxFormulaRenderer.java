package com.lessonweb.lesson.docx;

import com.lessonweb.lesson.docx.formula.LatexToMathmlConverter;
import com.lessonweb.lesson.docx.formula.MathmlToOmmlConverter;
import org.docx4j.XmlUtils;
import org.docx4j.wml.ContentAccessor;
import org.springframework.stereotype.Component;

@Component
public class DocxFormulaRenderer {

    private final LatexToMathmlConverter latexConverter;
    private final MathmlToOmmlConverter ommlConverter;

    public DocxFormulaRenderer(LatexToMathmlConverter latexConverter, MathmlToOmmlConverter ommlConverter) {
        this.latexConverter = latexConverter;
        this.ommlConverter = ommlConverter;
    }

    public boolean append(ContentAccessor target, String latex) {
        if (latex == null || latex.isBlank()) {
            return false;
        }
        try {
            String mathml = latexConverter.convert(latex.trim());
            target.getContent().add(XmlUtils.unmarshalString(ommlConverter.convert(mathml)));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public String toOmml(String latex) {
        return ommlConverter.convert(latexConverter.convert(latex.trim()));
    }
}
