import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.logging_utils import mask_token


class MaskTokenTest(unittest.TestCase):
    def test_masks_long_tokens_with_prefix_and_suffix(self) -> None:
        self.assertEqual(mask_token("1234567890abcdef"), "1234...cdef")

    def test_handles_missing_and_short_tokens(self) -> None:
        self.assertEqual(mask_token(None), "-")
        self.assertEqual(mask_token("ABCDEF", keep_start=2, keep_end=1), "AB...F")
