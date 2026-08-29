package com.lessonweb.lesson.docx.formula;

import org.springframework.stereotype.Component;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the MinerU LaTeX subset with a full TeX parser instead of a hand-written command list. */
@Component
public class LatexToMathmlConverter {

    private static final Pattern CONTROL_SEQUENCE = Pattern.compile("\\\\([A-Za-z]+)");
    private final SnuggleEngine engine = new SnuggleEngine();

    public String convert(String latex) {
        String value = latex == null ? "" : latex.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("LaTeX expression is blank");
        }
        try {
            validateCommands(value);
            SnuggleSession session = engine.createSession();
            boolean parsed = session.parseInput(new SnuggleInput("$" + value + "$"));
            if (!parsed || !session.getErrors().isEmpty()) {
                throw new IllegalArgumentException("Invalid LaTeX expression: " + session.getErrors());
            }
            String mathml = session.buildXMLString();
            if (!mathml.contains("<math")) {
                throw new IllegalArgumentException("LaTeX did not produce MathML");
            }
            return mathml;
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Unable to convert LaTeX", exception);
        }
    }

    private void validateCommands(String latex) {
        Matcher matcher = CONTROL_SEQUENCE.matcher(latex);
        while (matcher.find()) {
            String command = matcher.group(1);
            if (engine.getBuiltinCommandByTeXName(command) == null) {
                throw new IllegalArgumentException("Unsupported LaTeX command: " + command);
            }
        }
    }
}
