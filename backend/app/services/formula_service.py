import re

from sympy import simplify
from sympy.parsing.latex import parse_latex

from app.models.formula import FormulaValidationResult


class FormulaService:
    @staticmethod
    def normalize_latex(latex: str) -> str:
        value = latex.strip()
        if value.startswith("$") and value.endswith("$") and len(value) > 1:
            value = value[1:-1].strip()
        return (
            value.replace("−", "-")
            .replace("≤", r"\leq ")
            .replace("≥", r"\geq ")
            .replace("°", r"^{\circ}")
        )

    def validate(
        self, latex: str, reference_latex: str | None = None
    ) -> FormulaValidationResult:
        normalized = self.normalize_latex(latex)
        renderable = self._is_structurally_renderable(normalized)
        if not renderable:
            return FormulaValidationResult(
                latex=latex,
                normalized_latex=normalized,
                parseable=False,
                renderable=False,
                symbolic_supported=False,
                message="LaTeX 结构不完整，无法渲染；请结合原页修正。",
            )
        try:
            expressions = self._parse_expressions(self._normalize_for_symbolic(normalized))
        except Exception as exc:
            return FormulaValidationResult(
                latex=latex,
                normalized_latex=normalized,
                parseable=False,
                renderable=True,
                symbolic_supported=False,
                message=(
                    "LaTeX 结构可渲染，但此类展示公式暂不支持 SymPy 符号校验。"
                    f" 原因：{self._safe_error(exc)}"
                ),
            )

        equivalent: bool | None = None
        if reference_latex:
            try:
                references = self._parse_expressions(
                    self._normalize_for_symbolic(self.normalize_latex(reference_latex))
                )
                if len(expressions) == len(references) == 1:
                    equivalent = bool(simplify(expressions[0] - references[0]) == 0)
            except Exception:
                equivalent = None

        return FormulaValidationResult(
            latex=latex,
            normalized_latex=normalized,
            parseable=True,
            renderable=True,
            symbolic_supported=True,
            symbolic_expression="; ".join(map(str, expressions)),
            equivalent_to_reference=equivalent,
            message="SymPy 解析通过；这表示公式结构有效，不代表 OCR 与原图完全一致。",
        )

    @staticmethod
    def _is_structurally_renderable(latex: str) -> bool:
        depth = 0
        escaped = False
        for character in latex:
            if escaped:
                escaped = False
                continue
            if character == "\\":
                escaped = True
                continue
            if character == "{":
                depth += 1
            elif character == "}":
                depth -= 1
                if depth < 0:
                    return False
        return depth == 0 and not re.search(r"\\(?:frac|sqrt)\s*$", latex)

    @staticmethod
    def _normalize_for_symbolic(latex: str) -> str:
        value = latex.replace(r"\ ", " ")
        value = re.sub(r"([A-Za-z])_\{[^{}]+\}", r"\1sub", value)
        value = re.sub(r"\\mathrm\{([^{}]+)\}", r"\1", value)
        if r"\cdots" in value:
            raise ValueError("带余除法的省略号表达不属于单一符号等式")

        proportion = re.fullmatch(
            r"\s*([^:=]+?)\s*:\s*([^:=]+?)\s*=\s*([^:=]+?)\s*:\s*([^:=]+?)\s*",
            value,
        )
        if proportion:
            left_top, left_bottom, right_top, right_bottom = proportion.groups()
            return (
                rf"\frac{{{left_top.strip()}}}{{{left_bottom.strip()}}}"
                rf"=\frac{{{right_top.strip()}}}{{{right_bottom.strip()}}}"
            )
        if ":" in value and "=" not in value:
            value = value.replace(":", "/")
        return value

    @staticmethod
    def _parse_expressions(latex: str) -> list[object]:
        parse_value = re.sub(r"\^\{\\circ\}", "", latex)
        expressions: list[object] = []
        for segment in (part.strip() for part in parse_value.split(",")):
            if not segment:
                continue
            comparison = re.split(r"(\\leq|\\geq|<=|>=|<|>|=)", segment)
            if len(comparison) > 3:
                operands = comparison[0::2]
                operators = comparison[1::2]
                expressions.extend(
                    parse_latex(
                        f"{operands[index].strip()} {operator} {operands[index + 1].strip()}",
                        backend="lark",
                    )
                    for index, operator in enumerate(operators)
                )
            else:
                expressions.append(parse_latex(segment, backend="lark"))
        if not expressions:
            raise ValueError("公式内容为空")
        return expressions

    @staticmethod
    def _safe_error(exc: Exception) -> str:
        message = re.sub(r"\s+", " ", str(exc)).strip()
        return message[:180] or exc.__class__.__name__
