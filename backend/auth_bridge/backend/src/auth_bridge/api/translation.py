from fastapi import APIRouter, HTTPException, Request, status

from auth_bridge.middleware.rate_limit import (
    RateLimitExceeded,
    SlidingWindowRateLimiter,
    extract_client_ip,
)
from auth_bridge.schemas.translation import TranslationRequest, TranslationResponse
from auth_bridge.services.translation_service import (
    TranslationDisabledError,
    TranslationUpstreamError,
    UnsupportedTranslationPairError,
)


def build_translation_router() -> APIRouter:
    router = APIRouter(tags=["translation"])

    @router.post("", response_model=TranslationResponse)
    def translate(request_body: TranslationRequest, request: Request) -> TranslationResponse:
        try:
            _check_translation_rate_limit(request)
            service = request.app.state.translation_service
            translated = service.translate(
                text=request_body.text,
                source_language=request_body.source_language,
                target_language=request_body.target_language,
            )
        except RateLimitExceeded as exc:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many translation requests. Please try again later.",
            ) from exc
        except UnsupportedTranslationPairError as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Unsupported translation language pair.",
            ) from exc
        except TranslationDisabledError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Translation is not configured.",
            ) from exc
        except TranslationUpstreamError as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Translation provider is unavailable.",
            ) from exc

        return TranslationResponse(
            text=translated,
            source_language=request_body.source_language.strip().upper(),
            target_language=request_body.target_language.strip().upper(),
            provider=service.provider,
        )

    return router


def _check_translation_rate_limit(request: Request) -> None:
    limiter: SlidingWindowRateLimiter | None = getattr(request.app.state, "translation_rate_limiter", None)
    if limiter is None:
        return

    client_ip = extract_client_ip(
        headers=request.headers,
        client_host=request.client.host if request.client is not None else None,
        trusted_proxies=getattr(request.app.state, "trusted_proxy_networks", ()),
    )
    limiter.check(f"translation:ip:{client_ip}")
