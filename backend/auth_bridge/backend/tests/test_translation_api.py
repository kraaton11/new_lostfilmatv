import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app
from auth_bridge.services.translation_service import TranslationDisabledError


class TranslationApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        app.state.translation_rate_limiter.clear()
        self._original_translation_service = app.state.translation_service

    def tearDown(self) -> None:
        app.state.translation_service = self._original_translation_service

    def test_translate_returns_translated_text(self) -> None:
        app.state.translation_service = FakeTranslationService("Русское описание серии.")

        response = self.client.post(
            "/api/translate",
            json={
                "text": "English episode overview.",
                "sourceLanguage": "EN",
                "targetLanguage": "RU",
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json(),
            {
                "text": "Русское описание серии.",
                "source_language": "EN",
                "target_language": "RU",
                "provider": "fake",
            },
        )

    def test_translate_returns_service_unavailable_when_not_configured(self) -> None:
        app.state.translation_service = DisabledTranslationService()

        response = self.client.post(
            "/api/translate",
            json={
                "text": "English episode overview.",
                "sourceLanguage": "EN",
                "targetLanguage": "RU",
            },
        )

        self.assertEqual(response.status_code, 503)


class FakeTranslationService:
    provider = "fake"

    def __init__(self, translated_text: str) -> None:
        self._translated_text = translated_text

    def translate(self, text: str, source_language: str, target_language: str) -> str:
        self.last_request = (text, source_language, target_language)
        return self._translated_text


class DisabledTranslationService:
    provider = "fake"

    def translate(self, text: str, source_language: str, target_language: str) -> str:
        raise TranslationDisabledError("disabled")
