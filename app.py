import os
import sys
import asyncio
from contextlib import asynccontextmanager
import uvicorn

from fastapi import FastAPI, APIRouter
from src.influxdb.router import sleep_router
from src.mqtt.repository import listen_to_alarm_status
from src.mqtt.router import mqtt_router
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


api_router = APIRouter()
api_router.include_router(sleep_router)
api_router.include_router(mqtt_router)

@asynccontextmanager
async def lifespan(app: FastAPI):
    task = asyncio.create_task(listen_to_alarm_status())  # Запускаем фоновую задачу
    yield
    task.cancel()


app = FastAPI(title="Sleep Tracking API", lifespan=lifespan)
app.include_router(api_router, prefix="/api")


@app.get("/")
async def root():
    return {"message": "Welcome to Sleep Tracking API"}

if __name__ == "__main__":
    uvicorn.run(
        "app:app",
        host="10.147.19.211",
        port=8000,
        reload=True,
        workers=1
    )
