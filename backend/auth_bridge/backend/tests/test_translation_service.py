import sys
import unittest
from pathlib import Path
from urllib.parse import parse_qs

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.services.translation_service import (
    DeeplTranslationService,
    UnsupportedTranslationPairError,
)


class DeeplTranslationServiceTest(unittest.TestCase):
    def test_translate_posts_to_deepl_and_returns_first_translation(self) -> None:
        requests: list[httpx.Request] = []

        def handler(request: httpx.Request) -> httpx.Response:
            requests.append(request)
            return httpx.Response(
                status_code=200,
                json={"translations": [{"text": "Русское описание серии."}]},
            )

        service = DeeplTranslationService(
            api_key="test-key",
            api_url="https://api-free.deepl.com/v2/translate",
            timeout_seconds=3.0,
            transport=httpx.MockTransport(handler),
        )

        result = service.translate("English episode overview.", source_language="EN", target_language="RU")

        self.assertEqual(result, "Русское описание серии.")
        self.assertEqual(len(requests), 1)
        self.assertEqual(str(requests[0].url), "https://api-free.deepl.com/v2/translate")
        self.assertEqual(requests[0].headers["Authorization"], "DeepL-Auth-Key test-key")
        form = parse_qs(requests[0].content.decode("utf-8"))
        self.assertEqual(form["text"], ["English episode overview."])
        self.assertEqual(form["source_lang"], ["EN"])
        self.assertEqual(form["target_lang"], ["RU"])

    def test_translate_rejects_unsupported_language_pair(self) -> None:
        service = DeeplTranslationService(
            api_key="test-key",
            api_url="https://api-free.deepl.com/v2/translate",
            timeout_seconds=3.0,
        )

        with self.assertRaises(UnsupportedTranslationPairError):
            service.translate("Text", source_language="DE", target_language="RU")
