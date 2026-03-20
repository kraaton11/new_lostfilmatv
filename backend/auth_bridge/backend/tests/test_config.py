import os
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.config import Settings


class SettingsTest(unittest.TestCase):
    def test_public_base_url_accepts_legacy_app_base_url_env(self) -> None:
        with patch.dict(os.environ, {"APP_BASE_URL": "https://auth.bazuka.pp.ua"}, clear=True):
            settings = Settings()

        self.assertEqual(settings.public_base_url, "https://auth.bazuka.pp.ua")
        self.assertEqual(settings.wildcard_base_domain, "auth.bazuka.pp.ua")

    def test_public_base_domain_uses_explicit_wildcard_env(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AUTH_BRIDGE_PUBLIC_BASE_URL": "https://auth.bazuka.pp.ua",
                "AUTH_BRIDGE_PUBLIC_BASE_DOMAIN": "auth.bazuka.pp.ua",
            },
            clear=True,
        ):
            settings = Settings()

        self.assertEqual(settings.public_base_domain, "auth.bazuka.pp.ua")
        self.assertEqual(settings.wildcard_base_domain, "auth.bazuka.pp.ua")

    def test_settings_are_loaded_from_dotenv_file(self) -> None:
        with TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            (temp_path / ".env").write_text(
                "APP_BASE_URL=https://auth.bazuka.pp.ua\n"
                "AUTH_BRIDGE_PUBLIC_BASE_DOMAIN=auth.bazuka.pp.ua\n",
                encoding="utf-8",
            )

            original_cwd = Path.cwd()
            try:
                os.chdir(temp_path)
                with patch.dict(os.environ, {}, clear=True):
                    settings = Settings()
            finally:
                os.chdir(original_cwd)

        self.assertEqual(settings.public_base_url, "https://auth.bazuka.pp.ua")
        self.assertEqual(settings.wildcard_base_domain, "auth.bazuka.pp.ua")
