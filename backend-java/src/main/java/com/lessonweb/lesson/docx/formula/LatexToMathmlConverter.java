package com.lessonweb.lesson.docx.formula;

import org.springframework.stereotype.Component;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;
import uk.ac.ed.ph.snuggletex.SnugglePackage;
import uk.ac.ed.ph.snuggletex.definitions.LaTeXMode;
import uk.ac.ed.ph.snuggletex.definitions.TextFlowContext;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
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
            // SnuggleTeX has no CJK character mapping. Register expression-local text
            // atoms instead of letting its error recovery silently substitute 'x'.
            SnuggleEngine parser = new SnuggleEngine();
            SnugglePackage characters = new SnugglePackage("lesson-cjk");
            Set<Integer> registered = new HashSet<>();
            StringBuilder input = new StringBuilder();
            for (int cp : value.codePoints().toArray()) {
                if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.HAN) {
                    input.appendCodePoint(cp);
                    continue;
                }
                StringBuilder name = new StringBuilder("lessonhan");
                Integer.toHexString(cp).chars().forEach(digit -> name.append((char) ('a' + Character.digit(digit, 16))));
                if (registered.add(cp)) {
                    String text = new String(Character.toChars(cp));
                    characters.addSimpleCommand(name.toString(), EnumSet.of(LaTeXMode.MATH, LaTeXMode.LR),
                            (builder, parent, token) -> {
                                if (builder.isBuildingMathMLIsland()) builder.appendMathMLTextElement(parent, "mtext", text, false);
                                else builder.appendTextNode(parent, text, false);
                            }, TextFlowContext.ALLOW_INLINE);
                }
                input.append('\\').append(name).append(' ');
            }
            parser.addPackage(characters);
            // The provider/KaTeX spelling is \\text; SnuggleTeX's text box is \\mbox.
            value = CONTROL_SEQUENCE.matcher(input).replaceAll(match ->
                    Matcher.quoteReplacement("\\" + ("text".equals(match.group(1)) ? "mbox" : match.group(1))));
            SnuggleSession session = parser.createSession();
            boolean parsed = session.parseInput(new SnuggleInput("$" + value + "$"));
            if (!parsed || !session.getErrors().isEmpty()) {
                throw new IllegalArgumentException("Invalid LaTeX expression: " + session.getErrors());
            }
            String mathml = session.buildXMLString();
            if (!session.getErrors().isEmpty()) throw new IllegalArgumentException("Unable to build formula MathML");
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
            if (engine.getBuiltinCommandByTeXName("text".equals(command) ? "mbox" : command) == null) {
                throw new IllegalArgumentException("Unsupported LaTeX command: " + command);
            }
        }
    }
}
