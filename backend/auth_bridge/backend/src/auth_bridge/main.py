from fastapi import FastAPI

from auth_bridge.api.health import router as health_router

app = FastAPI(title="LostFilm Auth Bridge")
app.include_router(health_router)
