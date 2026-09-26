from fastapi import APIRouter, HTTPException, Request, Response, status
from starlette.concurrency import run_in_threadpool

from auth_bridge.middleware.rate_limit import (
    RateLimitExceeded,
    SlidingWindowRateLimiter,
    extract_client_ip,
)
from auth_bridge.services.kinopoisk_proxy_service import (
    KinopoiskProxyDisabledError,
    KinopoiskProxyPathError,
    KinopoiskProxyUpstreamError,
)


def build_kinopoisk_router() -> APIRouter:
    router = APIRouter(tags=["kinopoisk"])

    @router.get("/{path:path}")
    async def proxy_kinopoisk(path: str, request: Request) -> Response:
        try:
            _check_kinopoisk_rate_limit(request)
            service = request.app.state.kinopoisk_proxy_service
            proxied = await run_in_threadpool(
                service.fetch,
                path,
                list(request.query_params.multi_items()),
            )
        except RateLimitExceeded as exc:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many KinoPoisk proxy requests. Please try again later.",
            ) from exc
        except KinopoiskProxyPathError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Unsupported KinoPoisk endpoint.") from exc
        except KinopoiskProxyDisabledError as exc:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="KinoPoisk proxy is not configured.") from exc
        except KinopoiskProxyUpstreamError as exc:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="KinoPoisk upstream is unavailable.") from exc

        return Response(
            content=proxied.body,
            status_code=proxied.status_code,
            media_type=proxied.content_type,
        )

    return router


def _check_kinopoisk_rate_limit(request: Request) -> None:
    limiter: SlidingWindowRateLimiter | None = getattr(request.app.state, "kinopoisk_rate_limiter", None)
    if limiter is None:
        return

    client_ip = extract_client_ip(
        headers=request.headers,
        client_host=request.client.host if request.client is not None else None,
        trusted_proxies=getattr(request.app.state, "trusted_proxy_networks", ()),
    )
    limiter.check(f"kinopoisk:ip:{client_ip}")
