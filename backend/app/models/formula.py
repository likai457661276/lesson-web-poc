from pydantic import BaseModel, Field


class FormulaValidationRequest(BaseModel):
    latex: str = Field(min_length=1, max_length=4000)
    reference_latex: str | None = Field(default=None, alias="referenceLatex", max_length=4000)


class FormulaValidationResult(BaseModel):
    latex: str
    normalized_latex: str = Field(alias="normalizedLatex")
    parseable: bool
    symbolic_expression: str | None = Field(default=None, alias="symbolicExpression")
    equivalent_to_reference: bool | None = Field(default=None, alias="equivalentToReference")
    message: str

    model_config = {"populate_by_name": True, "serialize_by_alias": True}
