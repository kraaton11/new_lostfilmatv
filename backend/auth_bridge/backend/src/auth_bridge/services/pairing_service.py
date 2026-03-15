from secrets import token_hex
import string
from random import SystemRandom
from threading import RLock
from typing import Callable

from auth_bridge.config import Settings
from auth_bridge.schemas.pairing import PairingCreateResponse, PairingStatus, PairingStatusResponse
from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.lostfilm_login_client import LostFilmLoginClient, LostFilmLoginStep
from auth_bridge.services.pairing_store import InMemoryPairingStore, PairingRecord


class PairingNotFoundError(Exception):
    pass


class PairingNotReadyError(Exception):
    pass


class PairingAlreadyClaimedError(Exception):
    pass


class PairingExpiredError(Exception):
    pass


class PairingService:
    def __init__(self, store: InMemoryPairingStore, settings: Settings) -> None:
        self._store = store
        self._settings = settings
        self._random = SystemRandom()
        self._lock = RLock()

    def create_pairing(self) -> PairingCreateResponse:
        with self._lock:
            user_code = self._generate_unique_user_code()
            record = self._store.save(
                pairing_id=token_hex(16),
                user_code=user_code,
            )
            return self._build_create_response(record)

    def get_status(self, pairing_id: str) -> PairingStatusResponse:
        with self._lock:
            record = self._require_record(pairing_id)
            return PairingStatusResponse(
                pairingId=record.pairing_id,
                status=record.status,
                expiresIn=record.expires_in(),
            )

    def get_pairing_by_user_code(self, user_code: str) -> PairingRecord:
        with self._lock:
            record = self._store.get_by_user_code(user_code)
            if record is None:
                raise PairingNotFoundError
            return record

    def mark_phone_flow_opened(self, user_code: str) -> PairingRecord:
        with self._lock:
            record = self.get_pairing_by_user_code(user_code)
            if record.is_expired():
                raise PairingExpiredError
            if record.status == PairingStatus.PENDING:
                record.phone_flow_status = PairingStatus.AWAITING_PHONE_LOGIN
            return record

    def store_challenge_step(self, user_code: str, challenge_step: LostFilmLoginStep) -> PairingRecord:
        with self._lock:
            record = self.get_pairing_by_user_code(user_code)
            if record.is_expired():
                raise PairingExpiredError
            record.challenge_step = challenge_step
            record.phone_flow_status = PairingStatus.AWAITING_PHONE_CHALLENGE
            return record

    def get_challenge_step(self, user_code: str) -> LostFilmLoginStep:
        with self._lock:
            record = self.get_pairing_by_user_code(user_code)
            if record.is_expired():
                raise PairingExpiredError
            if record.challenge_step is None:
                raise PairingNotReadyError
            return record.challenge_step

    def mark_phone_flow_retryable(self, user_code: str) -> PairingRecord:
        with self._lock:
            record = self.get_pairing_by_user_code(user_code)
            if record.is_expired():
                raise PairingExpiredError
            record.challenge_step = None
            self._close_login_client(record)
            if record.session_payload is None:
                record.phone_flow_status = PairingStatus.AWAITING_PHONE_LOGIN
            return record

    def get_or_create_login_client(
        self,
        user_code: str,
        client_factory: Callable[[], LostFilmLoginClient],
    ) -> LostFilmLoginClient:
        with self._lock:
            record = self.get_pairing_by_user_code(user_code)
            if record.is_expired():
                raise PairingExpiredError
            if record.login_client is None:
                record.login_client = client_factory()
            return record.login_client

    def get_login_client(self, user_code: str) -> LostFilmLoginClient:
        with self._lock:
            record = self.get_pairing_by_user_code(user_code)
            if record.is_expired():
                raise PairingExpiredError
            if record.login_client is None:
                raise PairingNotReadyError
            return record.login_client

    def confirm_pairing(self, pairing_id: str, session_payload: dict | SessionPayload) -> None:
        with self._lock:
            record = self._require_record(pairing_id)
            if record.is_expired():
                raise PairingExpiredError
            if record.session_payload is not None:
                return
            record.session_payload = SessionPayload.model_validate(session_payload)
            record.challenge_step = None
            self._close_login_client(record)

    def confirm_pairing_by_user_code(self, user_code: str, session_payload: dict | SessionPayload) -> None:
        record = self.get_pairing_by_user_code(user_code)
        self.confirm_pairing(record.pairing_id, session_payload)

    def claim_session(self, pairing_id: str) -> SessionPayload:
        with self._lock:
            record = self._require_record(pairing_id)
            if record.is_expired():
                raise PairingExpiredError
            if record.session_payload is None:
                raise PairingNotReadyError
            if record.claimed:
                raise PairingAlreadyClaimedError
            record.claimed = True
            return record.session_payload

    def reset(self) -> None:
        with self._lock:
            self._store.clear()

    def _require_record(self, pairing_id: str) -> PairingRecord:
        record = self._store.get(pairing_id)
        if record is None:
            raise PairingNotFoundError
        return record

    def _build_create_response(self, record: PairingRecord) -> PairingCreateResponse:
        return PairingCreateResponse(
            pairingId=record.pairing_id,
            userCode=record.user_code,
            verificationUrl=f"{self._settings.public_base_url}/pair/{record.user_code}",
            expiresIn=record.expires_in(now=record.created_at),
            pollInterval=self._settings.pairing_poll_interval_seconds,
            status=record.status,
        )

    def _generate_user_code(self) -> str:
        alphabet = string.ascii_uppercase + string.digits
        return "".join(self._random.choice(alphabet) for _ in range(6))

    def _generate_unique_user_code(self) -> str:
        while True:
            user_code = self._generate_user_code()
            if not self._store.has_user_code(user_code):
                return user_code

    def _close_login_client(self, record: PairingRecord) -> None:
        if record.login_client is not None:
            record.login_client.close()
            record.login_client = None
