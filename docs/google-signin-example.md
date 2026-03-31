# Google Sign-In Example

Этот пример показывает правильную схему входа через Google для своего проекта.

Важно:

- не используйте чужие маршруты вроде `https://www.lostfilm.today/auth/gp/...`
- создайте свой OAuth client в Google Cloud
- на backend проверяйте Google ID token и создавайте уже свою сессию

## 1. Что настроить в Google Cloud

Создай Web application client ID и укажи:

- `Authorized JavaScript origins`: `https://your-domain.com`
- `Authorized redirect URIs`: если используешь redirect flow, например `https://your-domain.com/auth/google/callback`

Для варианта ниже достаточно origin и client ID.

## 2. Frontend

Минимальная HTML-страница:

```html
<!doctype html>
<html lang="ru">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Login</title>
    <script src="https://accounts.google.com/gsi/client" async></script>
  </head>
  <body>
    <h1>Вход</h1>

    <div
      id="g_id_onload"
      data-client_id="YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
      data-callback="handleGoogleCredential"
    ></div>

    <div
      class="g_id_signin"
      data-type="standard"
      data-shape="pill"
      data-theme="outline"
      data-text="signin_with"
      data-size="large"
    ></div>

    <script>
      async function handleGoogleCredential(response) {
        const result = await fetch("/auth/google", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({
            credential: response.credential
          })
        });

        if (!result.ok) {
          alert("Google login failed");
          return;
        }

        window.location.href = "/";
      }
    </script>
  </body>
</html>
```

## 3. Backend FastAPI

Установи зависимость:

```bash
pip install google-auth
```

Пример endpoint:

```python
from dataclasses import dataclass

from fastapi import FastAPI, HTTPException, Response
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token
from pydantic import BaseModel


app = FastAPI()


@dataclass
class Settings:
    google_client_id: str = "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"


settings = Settings()


class GoogleLoginRequest(BaseModel):
    credential: str


@app.post("/auth/google")
async def auth_google(payload: GoogleLoginRequest, response: Response) -> dict:
    try:
        token_payload = id_token.verify_oauth2_token(
            payload.credential,
            google_requests.Request(),
            settings.google_client_id,
        )
    except Exception as exc:
        raise HTTPException(status_code=401, detail="Invalid Google token") from exc

    google_user_id = token_payload["sub"]
    email = token_payload.get("email")
    email_verified = token_payload.get("email_verified", False)
    name = token_payload.get("name")
    avatar_url = token_payload.get("picture")

    # Здесь ищешь или создаешь локального пользователя в своей БД.
    local_user_id = upsert_user_from_google(
        google_user_id=google_user_id,
        email=email,
        email_verified=email_verified,
        name=name,
        avatar_url=avatar_url,
    )

    session_token = create_session_for_user(local_user_id)
    response.set_cookie(
        key="session",
        value=session_token,
        httponly=True,
        secure=True,
        samesite="lax",
        max_age=60 * 60 * 24 * 30,
    )

    return {
        "ok": True,
        "user": {
            "id": local_user_id,
            "email": email,
            "name": name,
        },
    }


def upsert_user_from_google(
    google_user_id: str,
    email: str | None,
    email_verified: bool,
    name: str | None,
    avatar_url: str | None,
) -> str:
    # Заглушка. Здесь должна быть работа с БД.
    return f"user:{google_user_id}"


def create_session_for_user(local_user_id: str) -> str:
    # Заглушка. Здесь должна быть генерация и сохранение сессии.
    return f"session-for:{local_user_id}"
```

## 4. Что хранить в базе

Для Google-пользователя сохраняй:

- `provider = "google"`
- `provider_user_id = sub`
- `email`
- `email_verified`
- `name`
- `avatar_url`

Как уникальный идентификатор используй именно `sub`, а не email.

## 5. Что не делать

Неправильно:

- дергать endpoint LostFilm у себя в проекте
- сохранять Google email как единственный и неизменный user id
- доверять токену без серверной проверки
- использовать только frontend без своей серверной сессии

## 6. Как встроить в текущий стек

Для этого репозитория логичное место для такой интеграции:

- backend endpoint: `backend/auth_bridge/backend/src/auth_bridge/api/...`
- настройки client ID: `backend/auth_bridge/backend/src/auth_bridge/config.py`
- переменные окружения: `backend/auth_bridge/.env.example`

Если решишь реально внедрять, следующий шаг такой:

1. добавить `google-auth` в зависимости backend
2. добавить `AUTH_BRIDGE_GOOGLE_CLIENT_ID`
3. поднять endpoint `/auth/google`
4. связать успешный Google login с локальной сессией приложения
