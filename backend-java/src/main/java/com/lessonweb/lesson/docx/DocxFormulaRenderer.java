package com.lessonweb.lesson.docx;

import com.lessonweb.lesson.docx.formula.LatexToMathmlConverter;
import com.lessonweb.lesson.exception.AppException;
import org.springframework.http.HttpStatus;
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

    public void append(ContentAccessor target, String latex) {
        try {
            String mathml = latexConverter.convert(latex);
            target.getContent().add(XmlUtils.unmarshalString(ommlConverter.convert(mathml)));
        } catch (Exception exception) {
            throw new AppException("DOCX_FORMULA_UNSUPPORTED",
                    "存在无法转换为 Word 公式的 LaTeX，请检查并修改公式后重试", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
