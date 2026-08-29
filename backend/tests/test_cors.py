from fastapi.testclient import TestClient

from app.main import app


def test_cors_allows_both_local_frontend_origins() -> None:
    with TestClient(app) as client:
        for origin in ("http://localhost:5173", "http://127.0.0.1:5173"):
            response = client.options(
                "/api/documents/parse",
                headers={
                    "Origin": origin,
                    "Access-Control-Request-Method": "POST",
                },
            )

            assert response.status_code == 200
            assert response.headers["access-control-allow-origin"] == origin
