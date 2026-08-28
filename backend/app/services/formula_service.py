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
        try:
            expressions = self._parse_expressions(normalized)
        except Exception as exc:
            return FormulaValidationResult(
                latex=latex,
                normalized_latex=normalized,
                parseable=False,
                message=f"SymPy 无法解析该 LaTeX：{self._safe_error(exc)}",
            )

        equivalent: bool | None = None
        if reference_latex:
            try:
                references = self._parse_expressions(
                    self.normalize_latex(reference_latex)
                )
                if len(expressions) == len(references) == 1:
                    equivalent = bool(simplify(expressions[0] - references[0]) == 0)
            except Exception:
                equivalent = None

        return FormulaValidationResult(
            latex=latex,
            normalized_latex=normalized,
            parseable=True,
            symbolic_expression="; ".join(map(str, expressions)),
            equivalent_to_reference=equivalent,
            message="SymPy 解析通过；这表示公式结构有效，不代表 OCR 与原图完全一致。",
        )

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
