package com.lessonweb.lesson.service;

import com.lessonweb.lesson.model.formula.FormulaValidationRequest;
import com.lessonweb.lesson.model.formula.FormulaValidationResult;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.form.tex.TeXParser;
import org.matheclipse.core.interfaces.IExpr;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FormulaValidationService {

    private static final Pattern COMPARISON = Pattern.compile("(\\\\leq|\\\\geq|<=|>=|<|>|=)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final PrintStream SYMJA_SINK = new PrintStream(OutputStream.nullOutputStream());

    public FormulaValidationResult validate(FormulaValidationRequest request) {
        String normalized = normalizeLatex(request.latex());
        List<IExpr> expressions;
        try {
            expressions = parseExpressions(normalized);
        } catch (Exception exception) {
            return new FormulaValidationResult(
                    request.latex(), normalized, false, null, null,
                    "Symja 无法解析该 LaTeX：" + safeError(exception));
        }

        Boolean equivalent = null;
        if (request.referenceLatex() != null && !request.referenceLatex().isBlank()) {
            try {
                List<IExpr> references = parseExpressions(normalizeLatex(request.referenceLatex()));
                if (expressions.size() == 1 && references.size() == 1) {
                    ExprEvaluator evaluator = newEvaluator();
                    IExpr difference = evaluator.evaluate("Simplify((" + expressions.get(0)
                            + ")-(" + references.get(0) + "))");
                    equivalent = difference.isZero();
                }
            } catch (Exception ignored) {
                equivalent = null;
            }
        }

        return new FormulaValidationResult(
                request.latex(), normalized, true,
                expressions.stream().map(Object::toString).reduce((left, right) -> left + "; " + right).orElse(""),
                equivalent,
                "Symja 解析通过；这表示公式结构有效，不代表 OCR 与原图完全一致。");
    }

    public String normalizeLatex(String latex) {
        String value = latex == null ? "" : latex.trim();
        if (value.length() > 1 && value.startsWith("$") && value.endsWith("$")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value.replace("−", "-")
                .replace("≤", "\\leq ")
                .replace("≥", "\\geq ")
                .replace("°", "^{\\circ}");
    }

    private List<IExpr> parseExpressions(String latex) {
        String parseValue = latex.replaceAll("\\^\\{\\\\circ}", "");
        List<IExpr> result = new ArrayList<>();
        for (String rawSegment : parseValue.split(",")) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) continue;
            Matcher matcher = COMPARISON.matcher(segment);
            List<String> operands = new ArrayList<>();
            List<String> operators = new ArrayList<>();
            int start = 0;
            while (matcher.find()) {
                operands.add(segment.substring(start, matcher.start()).trim());
                operators.add(matcher.group());
                start = matcher.end();
            }
            if (!operators.isEmpty()) operands.add(segment.substring(start).trim());
            if (operators.size() > 1) {
                for (int index = 0; index < operators.size(); index++) {
                    result.add(parse(operands.get(index) + " " + operators.get(index) + " " + operands.get(index + 1)));
                }
            } else {
                result.add(parse(segment));
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("公式内容为空");
        return result;
    }

    private IExpr parse(String latex) {
        validateDelimiters(latex);
        IExpr expression = new TeXParser().parse(latex);
        if (expression == null || !expression.isPresent()) {
            throw new IllegalArgumentException("无法生成符号表达式");
        }
        return newEvaluator().evaluate(expression);
    }

    private ExprEvaluator newEvaluator() {
        ExprEvaluator evaluator = new ExprEvaluator();
        evaluator.getEvalEngine().setQuietMode(true);
        evaluator.getEvalEngine().setOutPrintStream(SYMJA_SINK);
        evaluator.getEvalEngine().setErrorPrintStream(SYMJA_SINK);
        return evaluator;
    }

    private void validateDelimiters(String latex) {
        int braces = 0;
        int parentheses = 0;
        for (int index = 0; index < latex.length(); index++) {
            char current = latex.charAt(index);
            if (current == '{') braces++;
            if (current == '}' && --braces < 0) throw new IllegalArgumentException("花括号不匹配");
            if (current == '(') parentheses++;
            if (current == ')' && --parentheses < 0) throw new IllegalArgumentException("圆括号不匹配");
        }
        if (braces != 0) throw new IllegalArgumentException("花括号不匹配");
        if (parentheses != 0) throw new IllegalArgumentException("圆括号不匹配");
        if (latex.endsWith("\\")) throw new IllegalArgumentException("LaTeX 命令不完整");
    }

    private String safeError(Exception exception) {
        String message = WHITESPACE.matcher(String.valueOf(exception.getMessage())).replaceAll(" ").trim();
        if (message.isEmpty() || "null".equals(message)) message = exception.getClass().getSimpleName();
        return message.substring(0, Math.min(180, message.length()));
    }
}
