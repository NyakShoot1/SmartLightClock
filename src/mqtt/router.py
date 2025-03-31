from fastapi import APIRouter

from src.mqtt.repository import get_latest_sensor_data, send_alarm_time
from src.mqtt.schemas import SensorsData

aiomqtt_router = APIRouter(prefix="/mqtt", tags=["MQTT"])


@aiomqtt_router.get("/latest", response_model=SensorsData)
async def latest_sensor_data():
    return await get_latest_sensor_data()


@aiomqtt_router.post("/set_alarm")
async def set_alarm_time(time: int):
    await send_alarm_time(time)
    return {"message": "Alarm time set", "time": time}
