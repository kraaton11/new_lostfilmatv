from fastapi import APIRouter, HTTPException, Request, Response, status
from fastapi.responses import FileResponse
from starlette.concurrency import run_in_threadpool

from auth_bridge.middleware.rate_limit import (
    RateLimitExceeded,
    SlidingWindowRateLimiter,
    extract_client_ip,
)
from auth_bridge.services.tmdb_image_proxy_service import (
    TmdbImageInvalidPathError,
    TmdbImageNotFoundError,
    TmdbImageUpstreamError,
    etag_for_image_filename,
)
from auth_bridge.services.tmdb_proxy_service import (
    TmdbProxyDisabledError,
    TmdbProxyPathError,
    TmdbProxyUpstreamError,
)


IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable"


def build_tmdb_router() -> APIRouter:
    router = APIRouter(tags=["tmdb"])

    @router.get("/t/p/{size}/{file_path:path}")
    async def proxy_tmdb_image(
        size: str,
        file_path: str,
        request: Request,
    ) -> Response:
        try:
            _check_tmdb_image_rate_limit(request)
            service = request.app.state.tmdb_image_proxy_service

            # Валидируем путь до похода в сервис, чтобы отбить мусор дешёвой
            # проверкой, а не скачиванием файла с апстрима.
            _, filename, _ = service.validate_and_resolve(size, file_path)
            etag = etag_for_image_filename(filename)
            if_none_match = request.headers.get("if-none-match")
            if _etag_matches(if_none_match, etag):
                return _not_modified(etag)

            result = await run_in_threadpool(service.get_image, size, file_path)
        except RateLimitExceeded as exc:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many TMDB image requests. Please try again later.",
            ) from exc
        except TmdbImageInvalidPathError as exc:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
        except TmdbImageNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Image not found.") from exc
        except TmdbImageUpstreamError as exc:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="TMDB image upstream недоступен.") from exc

        if _etag_matches(if_none_match, result.etag):
            return _not_modified(result.etag)

        return FileResponse(
            path=result.file_path,
            media_type=result.content_type,
            headers={
                "ETag": result.etag,
                "Cache-Control": IMAGE_CACHE_CONTROL,
            },
        )

    @router.get("/{path:path}")
    async def proxy_tmdb(path: str, request: Request) -> Response:
        try:
            _check_tmdb_rate_limit(request)
            service = request.app.state.tmdb_proxy_service
            proxied = await run_in_threadpool(
                service.fetch,
                path,
                list(request.query_params.multi_items()),
            )
        except RateLimitExceeded as exc:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many TMDB proxy requests. Please try again later.",
            ) from exc
        except TmdbProxyPathError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Unsupported TMDB endpoint.") from exc
        except TmdbProxyDisabledError as exc:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="TMDB proxy is not configured.") from exc
        except TmdbProxyUpstreamError as exc:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="TMDB upstream is unavailable.") from exc

        return Response(
            content=proxied.body,
            status_code=proxied.status_code,
            media_type=proxied.content_type,
        )

    return router


def _etag_matches(if_none_match: str | None, etag: str) -> bool:
    if not if_none_match:
        return False
    candidates = {tag.strip() for tag in if_none_match.split(",")}
    return "*" in candidates or etag in candidates


def _not_modified(etag: str) -> Response:
    return Response(
        status_code=status.HTTP_304_NOT_MODIFIED,
        headers={"ETag": etag, "Cache-Control": IMAGE_CACHE_CONTROL},
    )


def _check_tmdb_rate_limit(request: Request) -> None:
    limiter: SlidingWindowRateLimiter | None = getattr(request.app.state, "tmdb_rate_limiter", None)
    if limiter is None:
        return

    client_ip = extract_client_ip(
        headers=request.headers,
        client_host=request.client.host if request.client is not None else None,
        trusted_proxies=getattr(request.app.state, "trusted_proxy_networks", ()),
    )
    limiter.check(f"tmdb:ip:{client_ip}")


def _check_tmdb_image_rate_limit(request: Request) -> None:
    limiter: SlidingWindowRateLimiter | None = getattr(request.app.state, "tmdb_image_rate_limiter", None)
    if limiter is None:
        return

    client_ip = extract_client_ip(
        headers=request.headers,
        client_host=request.client.host if request.client is not None else None,
        trusted_proxies=getattr(request.app.state, "trusted_proxy_networks", ()),
    )
    limiter.check(f"tmdb_image:ip:{client_ip}")
