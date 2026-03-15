from pydantic import BaseModel


class LostFilmCookie(BaseModel):
    name: str
    value: str
    domain: str
    path: str = "/"


class SessionPayload(BaseModel):
    cookies: list[LostFilmCookie]
    accountId: str | None = None
