from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_formula_validation_accepts_parseable_latex() -> None:
    response = client.post(
        "/api/formulas/validate",
        json={"latex": r"x^2 + 2x + 1"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["parseable"] is True
    assert payload["renderable"] is True
    assert payload["symbolicSupported"] is True
    assert payload["symbolicExpression"]


def test_formula_validation_reports_invalid_latex() -> None:
    response = client.post(
        "/api/formulas/validate",
        json={"latex": r"\frac{"},
    )

    assert response.status_code == 200
    assert response.json()["parseable"] is False
    assert response.json()["renderable"] is False


def test_formula_validation_handles_degree_lists_and_chained_inequalities() -> None:
    formulas = [
        r"a=30^{\circ},a=-60^{\circ},a=120^{\circ},a=-135^{\circ}",
        r"-360^{\circ} \leq b < 720^{\circ}",
    ]

    for latex in formulas:
        response = client.post("/api/formulas/validate", json={"latex": latex})
        assert response.status_code == 200
        assert response.json()["parseable"] is True


def test_formula_validation_distinguishes_renderable_display_math() -> None:
    formulas = [
        r"8 \div 3 = 2 \cdots 2",
        r"S_{侧} = 2\pi rh",
    ]

    responses = [client.post("/api/formulas/validate", json={"latex": latex}).json() for latex in formulas]

    assert responses[0]["parseable"] is False
    assert responses[0]["renderable"] is True
    assert responses[0]["symbolicSupported"] is False
    assert responses[1]["renderable"] is True
