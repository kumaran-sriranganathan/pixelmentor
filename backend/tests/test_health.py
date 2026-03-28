# ── Lessons ────────────────────────────────────────────────────────────────────

class TestLessons:
    def test_list_returns_200_and_list(self, client):
        from unittest.mock import MagicMock
        from app.routers.lessons import get_search_client

        mock = MagicMock()
        mock_results = MagicMock()
        mock_results.__iter__ = MagicMock(return_value=iter([
            {"id": "lesson-001", "title": "Test", "description": "Desc",
             "category": "fundamentals", "difficulty": "beginner",
             "duration_minutes": 15, "is_pro": False, "order": 1, "tags": []}
        ]))
        mock_results.get_count = MagicMock(return_value=1)
        mock.search.return_value = mock_results
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/api/v1/lessons")
        assert resp.status_code == 200
        assert isinstance(resp.json()["lessons"], list)

        app.dependency_overrides.pop(get_search_client, None)

    def test_list_items_have_required_fields(self, client):
        from unittest.mock import MagicMock
        from app.routers.lessons import get_search_client

        mock = MagicMock()
        mock_results = MagicMock()
        mock_results.__iter__ = MagicMock(return_value=iter([
            {"id": "lesson-001", "title": "Test", "description": "Desc",
             "category": "fundamentals", "difficulty": "beginner",
             "duration_minutes": 15, "is_pro": False, "order": 1, "tags": []}
        ]))
        mock_results.get_count = MagicMock(return_value=1)
        mock.search.return_value = mock_results
        app.dependency_overrides[get_search_client] = lambda: mock

        lessons = client.get("/api/v1/lessons").json()["lessons"]
        assert len(lessons) > 0
        for lesson in lessons:
            assert {"id", "title", "difficulty"} <= lesson.keys()

        app.dependency_overrides.pop(get_search_client, None)

    def test_filter_by_skill_level(self, client):
        from unittest.mock import MagicMock
        from app.routers.lessons import get_search_client

        mock = MagicMock()
        mock_results = MagicMock()
        mock_results.__iter__ = MagicMock(return_value=iter([]))
        mock_results.get_count = MagicMock(return_value=0)
        mock.search.return_value = mock_results
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/api/v1/lessons?difficulty=beginner")
        assert resp.status_code == 200

        app.dependency_overrides.pop(get_search_client, None)

    def test_get_by_id(self, client):
        from unittest.mock import MagicMock
        from app.routers.lessons import get_search_client

        mock = MagicMock()
        mock.get_document.return_value = {
            "id": "lesson-001", "title": "Test", "description": "Desc",
            "category": "fundamentals", "difficulty": "beginner",
            "duration_minutes": 15, "is_pro": False, "order": 1,
            "tags": [], "content": "Full content."
        }
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/api/v1/lessons/lesson-001")
        assert resp.status_code == 200
        assert resp.json()["id"] == "lesson-001"

        app.dependency_overrides.pop(get_search_client, None)

    def test_not_found_returns_404(self, client):
        from unittest.mock import MagicMock
        from azure.core.exceptions import HttpResponseError
        from app.routers.lessons import get_search_client

        mock = MagicMock()
        err = HttpResponseError(message="Not found")
        err.status_code = 404
        mock.get_document.side_effect = err
        app.dependency_overrides[get_search_client] = lambda: mock

        assert client.get("/api/v1/lessons/does-not-exist-999").status_code == 404

        app.dependency_overrides.pop(get_search_client, None)