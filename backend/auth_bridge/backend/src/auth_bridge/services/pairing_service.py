from datetime import UTC, datetime, timedelta
from secrets import token_hex
import httpx
import logging
import string
from random import SystemRandom
from threading import RLock
from urllib.parse import urlsplit

from auth_bridge.config import Settings
from auth_bridge.logging_utils import mask_token
from auth_bridge.schemas.pairing import PairingCreateResponse, PairingStatus, PairingStatusResponse
from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.pairing_store import InMemoryPairingStore, PairingRecord

logger = logging.getLogger(__name__)


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
            logger.info(
                "Pairing created: pairing_id=%s user_code=%s",
                mask_token(record.pairing_id),
                mask_token(record.user_code, keep_start=2, keep_end=1),
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

    def get_pairing(self, pairing_id: str) -> PairingRecord:
        with self._lock:
            return self._require_record(pairing_id)

    def healthcheck(self) -> None:
        with self._lock:
            if not self._wildcard_base_domain():
                raise RuntimeError("wildcard base domain is not configured")
            self._store.healthcheck()

    def open_phone_flow(self, phone_verifier: str) -> PairingRecord:
        with self._lock:
            record = self.get_pairing_by_phone_verifier(phone_verifier)
            if record.is_expired():
                raise PairingExpiredError
            if record.session_payload is None and record.failure_reason is None:
                record.phone_flow_status = PairingStatus.IN_PROGRESS
            return record

    def open_phone_flow_for_host(self, host: str) -> PairingRecord:
        return self.open_phone_flow(self.resolve_phone_verifier_from_host(host))

    def build_verification_url(self, phone_verifier: str) -> str:
        return f"https://{phone_verifier}.{self._wildcard_base_domain()}/"

    def normalize_wildcard_host(self, host: str) -> str:
        normalized_host = _normalize_host_header(host)
        wildcard_base_domain = self._wildcard_base_domain()
        expected_suffix = f".{wildcard_base_domain}"
        if not normalized_host.endswith(expected_suffix):
            raise PairingNotFoundError
        return normalized_host

    def resolve_phone_verifier_from_host(self, host: str) -> str:
        normalized_host = self.normalize_wildcard_host(host)
        expected_suffix = f".{self._wildcard_base_domain()}"
        phone_verifier = normalized_host[: -len(expected_suffix)]
        if not phone_verifier or "." in phone_verifier:
            raise PairingNotFoundError
        return phone_verifier

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
                logger.info("Claim lease opened for pairing_id=%s", mask_token(pairing_id))
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
            logger.info("Pairing finalized: pairing_id=%s", mask_token(pairing_id))

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
            logger.info("Claim released (session invalid) for pairing_id=%s", mask_token(pairing_id))

    def confirm_pairing(self, pairing_id: str, session_payload: dict | SessionPayload) -> None:
        with self._lock:
            record = self._require_record(pairing_id)
            if record.is_expired():
                raise PairingExpiredError
            record.session_payload = SessionPayload.model_validate(session_payload)
            record.retryable = None
            record.failure_reason = None

    def confirm_pairing_from_proxy_session(self, pairing_id: str, cookie_jar: httpx.Cookies) -> SessionPayload:
        with self._lock:
            record = self._require_record(pairing_id)
            if record.is_expired():
                raise PairingExpiredError
            session_payload = self._build_session_payload_from_cookie_jar(cookie_jar)
            record.session_payload = session_payload
            record.retryable = None
            record.failure_reason = None
            logger.info(
                "Pairing confirmed from proxied browser session: pairing_id=%s account_id=%s",
                mask_token(pairing_id),
                mask_token(session_payload.accountId),
            )
            return session_payload

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
            logger.warning("Claim lease expired for pairing_id=%s", mask_token(record.pairing_id))

    def _build_create_response(self, record: PairingRecord) -> PairingCreateResponse:
        return PairingCreateResponse(
            pairingId=record.pairing_id,
            pairingSecret=record.pairing_secret,
            phoneVerifier=record.phone_verifier,
            userCode=record.user_code,
            verificationUrl=self.build_verification_url(record.phone_verifier),
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

    def _wildcard_base_domain(self) -> str:
        return self._settings.wildcard_base_domain.strip().lower().rstrip(".")

    def _build_session_payload_from_cookie_jar(self, cookie_jar: httpx.Cookies) -> SessionPayload:
        cookies = [
            {
                "name": cookie.name,
                "value": cookie.value,
                "domain": cookie.domain,
                "path": cookie.path or "/",
            }
            for cookie in cookie_jar.jar
        ]
        account_cookie = next((cookie for cookie in cookie_jar.jar if cookie.name == "uid"), None)
        return SessionPayload(cookies=cookies, accountId=account_cookie.value if account_cookie is not None else None)


def _normalize_host_header(host: str) -> str:
    raw_host = host.strip()
    if not raw_host:
        raise PairingNotFoundError

    try:
        parsed = urlsplit(f"//{raw_host}", scheme="https")
        _ = parsed.port
    except ValueError as exc:
        raise PairingNotFoundError from exc

    if parsed.username is not None or parsed.password is not None:
        raise PairingNotFoundError
    if parsed.path or parsed.query or parsed.fragment:
        raise PairingNotFoundError

    normalized_host = (parsed.hostname or "").strip().lower().rstrip(".")
    if not normalized_host or " " in normalized_host or ".." in normalized_host:
        raise PairingNotFoundError
    return normalized_host
