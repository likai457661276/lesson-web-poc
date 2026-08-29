package com.lessonweb.lesson.docx.formula;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LatexToMathmlConverter {

    private static final Map<String, String> SYMBOLS = Map.ofEntries(
            Map.entry("le", "≤"), Map.entry("leq", "≤"), Map.entry("ge", "≥"),
            Map.entry("geq", "≥"), Map.entry("neq", "≠"), Map.entry("times", "×"),
            Map.entry("cdot", "·"), Map.entry("pm", "±"), Map.entry("infty", "∞"),
            Map.entry("alpha", "α"), Map.entry("beta", "β"), Map.entry("gamma", "γ"),
            Map.entry("theta", "θ"), Map.entry("pi", "π"), Map.entry("circ", "°"),
            Map.entry("sin", "sin"), Map.entry("cos", "cos"), Map.entry("tan", "tan")
    );

    public String convert(String latex) {
        Parser parser = new Parser(latex == null ? "" : latex.trim());
        String body = parser.parseSequence('\0');
        if (body.isBlank() || !parser.atEnd()) {
            throw new IllegalArgumentException("Invalid LaTeX expression");
        }
        return "<math xmlns=\"http://www.w3.org/1998/Math/MathML\"><mrow>" + body + "</mrow></math>";
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private boolean atEnd() {
            skipSpaces();
            return position == input.length();
        }

        private String parseSequence(char terminator) {
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                skipSpaces();
                if (position >= input.length() || (terminator != '\0' && input.charAt(position) == terminator)) {
                    break;
                }
                if (input.charAt(position) == '}') {
                    throw new IllegalArgumentException("Unexpected closing brace");
                }
                String atom = parseAtom();
                String sub = null;
                String sup = null;
                while (position < input.length() && (input.charAt(position) == '^' || input.charAt(position) == '_')) {
                    char marker = input.charAt(position++);
                    String argument = parseArgument();
                    if (marker == '^') {
                        sup = argument;
                    } else {
                        sub = argument;
                    }
                }
                if (sub != null && sup != null) {
                    atom = "<msubsup>" + atom + wrap(sub) + wrap(sup) + "</msubsup>";
                } else if (sub != null) {
                    atom = "<msub>" + atom + wrap(sub) + "</msub>";
                } else if (sup != null) {
                    atom = "<msup>" + atom + wrap(sup) + "</msup>";
                }
                result.append(atom);
            }
            return result.toString();
        }

        private String parseAtom() {
            char current = input.charAt(position++);
            if (current == '{') {
                String nested = parseSequence('}');
                require('}');
                return wrap(nested);
            }
            if (current == '\\') {
                String command = readCommand();
                if ("frac".equals(command)) {
                    return "<mfrac>" + wrap(parseArgument()) + wrap(parseArgument()) + "</mfrac>";
                }
                if ("sqrt".equals(command)) {
                    return "<msqrt>" + wrap(parseArgument()) + "</msqrt>";
                }
                if ("left".equals(command) || "right".equals(command)) {
                    return position < input.length() ? operator(String.valueOf(input.charAt(position++))) : "";
                }
                String symbol = SYMBOLS.get(command);
                if (symbol == null) {
                    throw new IllegalArgumentException("Unsupported LaTeX command: " + command);
                }
                return Character.isLetter(symbol.codePointAt(0)) ? identifier(symbol) : operator(symbol);
            }
            if (Character.isDigit(current) || current == '.') {
                int start = position - 1;
                while (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
                    position++;
                }
                return "<mn>" + escape(input.substring(start, position)) + "</mn>";
            }
            if (Character.isLetter(current)) {
                return identifier(String.valueOf(current));
            }
            if ("+-=<>(),[]|".indexOf(current) >= 0) {
                return operator(String.valueOf(current));
            }
            throw new IllegalArgumentException("Unsupported LaTeX character: " + current);
        }

        private String parseArgument() {
            skipSpaces();
            if (position >= input.length()) {
                throw new IllegalArgumentException("Missing LaTeX argument");
            }
            if (input.charAt(position) == '{') {
                position++;
                String nested = parseSequence('}');
                require('}');
                return nested;
            }
            return parseAtom();
        }

        private String readCommand() {
            int start = position;
            while (position < input.length() && Character.isLetter(input.charAt(position))) {
                position++;
            }
            if (start == position && position < input.length()) {
                return String.valueOf(input.charAt(position++));
            }
            return input.substring(start, position);
        }

        private void require(char expected) {
            if (position >= input.length() || input.charAt(position++) != expected) {
                throw new IllegalArgumentException("Unclosed LaTeX group");
            }
        }

        private void skipSpaces() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }
    }

    private static String wrap(String value) {
        return "<mrow>" + value + "</mrow>";
    }

    private static String identifier(String value) {
        return "<mi>" + escape(value) + "</mi>";
    }

    private static String operator(String value) {
        return "<mo>" + escape(value) + "</mo>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
