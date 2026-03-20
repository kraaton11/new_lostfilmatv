from dataclasses import dataclass, field
from datetime import UTC, datetime
from threading import RLock

import httpx

from auth_bridge.services.pairing_store import InMemoryPairingStore, PairingRecord


@dataclass
class ProxySessionState:
    cookie_jar: httpx.Cookies = field(default_factory=httpx.Cookies)
    last_seen_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    lock: RLock = field(default_factory=RLock)
    login_succeeded: bool = False


class ProxySessionStore:
    def __init__(self, pairing_store: InMemoryPairingStore) -> None:
        self._states: dict[str, ProxySessionState] = {}
        pairing_store.register_cleanup_callback(self._cleanup_pairing_state)

    def get(self, pairing_id: str) -> ProxySessionState | None:
        return self._states.get(pairing_id)

    def get_or_create(self, pairing_id: str) -> ProxySessionState:
        state = self._states.get(pairing_id)
        if state is None:
            state = ProxySessionState()
            self._states[pairing_id] = state
        state.last_seen_at = datetime.now(UTC)
        return state

    def clear(self, pairing_id: str) -> None:
        self._states.pop(pairing_id, None)

    def _cleanup_pairing_state(self, pairing: PairingRecord) -> None:
        self.clear(pairing.pairing_id)
