import sys
import unittest
from pathlib import Path
import json
import logging

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.logging_utils import JsonLogFormatter, mask_token


class MaskTokenTest(unittest.TestCase):
    def test_masks_long_tokens_with_prefix_and_suffix(self) -> None:
        self.assertEqual(mask_token("1234567890abcdef"), "1234...cdef")

    def test_handles_missing_and_short_tokens(self) -> None:
        self.assertEqual(mask_token(None), "-")
        self.assertEqual(mask_token("ABCDEF", keep_start=2, keep_end=1), "AB...F")


class JsonLogFormatterTest(unittest.TestCase):
    def test_formats_record_as_json_without_extra_whitespace(self) -> None:
        formatter = JsonLogFormatter()
        record = logging.LogRecord(
            name="auth_bridge.test",
            level=logging.INFO,
            pathname=__file__,
            lineno=1,
            msg="Pairing created: %s",
            args=("demo",),
            exc_info=None,
        )

        payload = json.loads(formatter.format(record))

        self.assertEqual(payload["level"], "INFO")
        self.assertEqual(payload["logger"], "auth_bridge.test")
        self.assertEqual(payload["message"], "Pairing created: demo")
        self.assertIn("ts", payload)
