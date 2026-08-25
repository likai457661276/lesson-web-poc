from fastapi import APIRouter

from app.models.formula import FormulaValidationRequest, FormulaValidationResult
from app.services.formula_service import FormulaService

router = APIRouter(prefix="/api/formulas", tags=["formulas"])
service = FormulaService()


@router.post("/validate", response_model=FormulaValidationResult)
def validate_formula(payload: FormulaValidationRequest) -> FormulaValidationResult:
    return service.validate(payload.latex, payload.reference_latex)
