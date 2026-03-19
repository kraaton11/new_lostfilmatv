from datetime import UTC, datetime, timedelta
from secrets import token_hex
import string
from random import SystemRandom
from threading import RLock
from typing import Callable, cast

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


class PairingForbiddenError(Exception):
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
                pairing_secret=token_hex(16),
                phone_verifier=token_hex(16),
                user_code=user_code,
            )
            return self._build_create_response(record)

    def get_status(self, pairing_id: str, pairing_secret: str) -> PairingStatusResponse:
        with self._lock:
            record = self._require_record(pairing_id)
            self._require_secret(record, pairing_secret)
            self._expire_claim_lease_if_needed(record)
            return PairingStatusResponse(
                pairingId=record.pairing_id,
                status=record.status,
                expiresIn=record.expires_in(),
                retryable=record.retryable,
                failureReason=record.failure_reason,
            )

    def get_pairing_by_phone_verifier(self, phone_verifier: str) -> PairingRecord:
        with self._lock:
            record = self._store.get_by_phone_verifier(phone_verifier)
            if record is None:
                raise PairingNotFoundError
            return record

    def open_phone_flow(self, phone_verifier: str) -> PairingRecord:
        with self._lock:
            record = self.get_pairing_by_phone_verifier(phone_verifier)
            if record.is_expired():
                raise PairingExpiredError
            if record.session_payload is None and record.failure_reason is None:
                record.phone_flow_status = PairingStatus.IN_PROGRESS
            return record

    def submit_phone_login(
        self,
        phone_verifier: str,
        username: str,
        password: str,
        client_factory: Callable[[], LostFilmLoginClient],
    ) -> SessionPayload | LostFilmLoginStep:
        with self._lock:
            record = self.get_pairing_by_phone_verifier(phone_verifier)
            if record.is_expired():
                raise PairingExpiredError
            if record.session_payload is not None:
                return record.session_payload
            if record.login_client is None:
                record.login_client = client_factory()
            login_client = record.login_client

        try:
            login_step = login_client.fetch_login_step()
            payload = login_client.submit_credentials(login_step, username=username, password=password)
        except Exception:
            with self._lock:
                record = self.get_pairing_by_phone_verifier(phone_verifier)
                record.phone_flow_status = PairingStatus.IN_PROGRESS
                record.retryable = True
                record.failure_reason = None
                self._close_login_client(record)
            raise

        with self._lock:
            record = self.get_pairing_by_phone_verifier(phone_verifier)
            if isinstance(payload, LostFilmLoginStep):
                record.challenge_step = payload if payload.step_kind == "challenge" else None
                record.phone_flow_status = PairingStatus.IN_PROGRESS
                record.retryable = True
                record.failure_reason = None
                return payload
            session_payload = SessionPayload.model_validate(cast(dict | SessionPayload, payload))
            record.session_payload = session_payload
            record.challenge_step = None
            record.retryable = None
            record.failure_reason = None
            self._close_login_client(record)
            return session_payload

    def complete_phone_challenge(
        self,
        phone_verifier: str,
        username: str,
        password: str,
        captcha_code: str,
    ) -> SessionPayload | LostFilmLoginStep:
        with self._lock:
            record = self.get_pairing_by_phone_verifier(phone_verifier)
            if record.is_expired():
                raise PairingExpiredError
            if record.challenge_step is None or record.login_client is None:
                raise PairingNotReadyError
            challenge_step = record.challenge_step
            login_client = record.login_client

        try:
            result = login_client.complete_challenge(
                challenge_step,
                captcha_code=captcha_code,
                username=username,
                password=password,
            )
        except Exception:
            with self._lock:
                record = self.get_pairing_by_phone_verifier(phone_verifier)
                record.phone_flow_status = PairingStatus.IN_PROGRESS
                record.retryable = True
                record.failure_reason = None
            raise

        with self._lock:
            record = self.get_pairing_by_phone_verifier(phone_verifier)
            if isinstance(result, LostFilmLoginStep):
                record.challenge_step = result if result.step_kind == "challenge" else None
                record.phone_flow_status = PairingStatus.IN_PROGRESS
                record.retryable = True
                record.failure_reason = None
                return result
            session_payload = SessionPayload.model_validate(cast(dict | SessionPayload, result))
            record.session_payload = session_payload
            record.challenge_step = None
            record.retryable = None
            record.failure_reason = None
            self._close_login_client(record)
            return session_payload

    def claim_session(self, pairing_id: str, pairing_secret: str) -> SessionPayload:
        with self._lock:
            record = self._require_record(pairing_id)
            self._require_secret(record, pairing_secret)
            self._expire_claim_lease_if_needed(record)
            if record.is_expired():
                raise PairingExpiredError
            if record.failure_reason == "lease_expired":
                raise PairingExpiredError
            if record.finalized:
                raise PairingAlreadyClaimedError
            if record.session_payload is None:
                raise PairingNotReadyError
            if not record.lease_active:
                record.lease_active = True
                record.claim_lease_expires_at = datetime.now(UTC) + timedelta(seconds=self._settings.claim_lease_ttl_seconds)
            return record.session_payload

    def finalize_claim(self, pairing_id: str, pairing_secret: str) -> None:
        with self._lock:
            record = self._require_record(pairing_id)
            self._require_secret(record, pairing_secret)
            self._expire_claim_lease_if_needed(record)
            if record.failure_reason == "lease_expired":
                raise PairingExpiredError
            if not record.lease_active or record.session_payload is None:
                raise PairingNotReadyError
            record.finalized = True
            record.lease_active = False
            record.claim_lease_expires_at = None

    def release_claim(self, pairing_id: str, pairing_secret: str) -> None:
        with self._lock:
            record = self._require_record(pairing_id)
            self._require_secret(record, pairing_secret)
            record.lease_active = False
            record.claim_lease_expires_at = None
            record.session_payload = None
            record.phone_flow_status = PairingStatus.FAILED
            record.retryable = True
            record.failure_reason = "session_invalid"

    def confirm_pairing(self, pairing_id: str, session_payload: dict | SessionPayload) -> None:
        with self._lock:
            record = self._require_record(pairing_id)
            if record.is_expired():
                raise PairingExpiredError
            record.session_payload = SessionPayload.model_validate(session_payload)
            record.retryable = None
            record.failure_reason = None

    def reset(self) -> None:
        with self._lock:
            self._store.clear()

    def _require_record(self, pairing_id: str) -> PairingRecord:
        record = self._store.get(pairing_id)
        if record is None:
            raise PairingNotFoundError
        return record

    def _require_secret(self, record: PairingRecord, pairing_secret: str) -> None:
        if not pairing_secret or record.pairing_secret != pairing_secret:
            raise PairingForbiddenError

    def _expire_claim_lease_if_needed(self, record: PairingRecord) -> None:
        if record.lease_active and record.claim_lease_expires_at is not None and datetime.now(UTC) >= record.claim_lease_expires_at:
            record.lease_active = False
            record.claim_lease_expires_at = None
            record.session_payload = None
            record.phone_flow_status = PairingStatus.FAILED
            record.retryable = True
            record.failure_reason = "lease_expired"

    def _build_create_response(self, record: PairingRecord) -> PairingCreateResponse:
        return PairingCreateResponse(
            pairingId=record.pairing_id,
            pairingSecret=record.pairing_secret,
            phoneVerifier=record.phone_verifier,
            userCode=record.user_code,
            verificationUrl=f"{self._settings.public_base_url}/pair/{record.phone_verifier}",
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
