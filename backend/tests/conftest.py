"""
Pytest configuration and shared fixtures for SHE-SHIELD backend tests.
"""

import os
import sys

# ---------------------------------------------------------------------------
# Ensure the `backend/` directory is on sys.path so `import main` works
# regardless of where pytest is invoked.
# ---------------------------------------------------------------------------
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

# ---------------------------------------------------------------------------
# Set required env vars BEFORE main.py is imported (load_dotenv will NOT
# override vars that already exist).  Keeps tests independent of .env file.
# ---------------------------------------------------------------------------
os.environ["SUPABASE_URL"] = "https://test.supabase.co"
os.environ["SUPABASE_SERVICE_ROLE_KEY"] = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXN0Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSJ9.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
os.environ["FAST2SMS_API_KEY"] = "test-fast2sms-key"
os.environ["TRACKING_BASE_URL"] = "https://test.example.com/track"

import pytest
from unittest.mock import MagicMock, patch

# ---------------------------------------------------------------------------
# Patch create_client at import-time so main.py's module-level call
# doesn't hit real Supabase.  The mock client is stored so fixtures can
# configure return values.
# ---------------------------------------------------------------------------
_mock_supabase_client = MagicMock()

with patch("supabase.create_client", return_value=_mock_supabase_client):
    import main  # noqa: F401  – forces module initialisation under mock


@pytest.fixture(autouse=True)
def mock_supabase():
    """
    Replace the live Supabase client with a fresh MagicMock
    for every test and restore after.  Also reset the rate
    limiter so tests are never flaky due to cross-test state.
    """
    fresh_mock = MagicMock()
    original = main.supabase
    main.supabase = fresh_mock

    # Disable rate limiting for every test
    main.app.state.limiter.enabled = False
    # Clear in-memory rate-limit storage between tests
    if hasattr(main.app.state.limiter, '_storage'):
        main.app.state.limiter._storage.reset()
    if hasattr(main.app.state.limiter, '_limiter'):
        try:
            main.app.state.limiter._limiter.reset()
        except Exception:
            pass

    yield fresh_mock
    main.supabase = original


@pytest.fixture()
def client(mock_supabase):
    """
    FastAPI TestClient wired to the application.
    Rate-limiting is disabled so tests aren't flaky.
    """
    from fastapi.testclient import TestClient
    from main import app

    # Disable rate limiting during tests
    from slowapi import Limiter
    from slowapi.util import get_remote_address

    app.state.limiter = Limiter(
        key_func=get_remote_address,
        enabled=False,
    )

    with TestClient(app) as tc:
        yield tc
