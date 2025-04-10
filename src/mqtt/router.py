from fastapi import APIRouter

from src.mqtt.repository import get_latest_sensor_data, send_alarm_time, check_if_can_rate_sleep_today, cancel_alarm, get_alarm_status
from src.mqtt.schemas import SensorsData

mqtt_router = APIRouter(prefix="/mqtt", tags=["MQTT"])


@mqtt_router.get("/latest", response_model=SensorsData)
async def latest_sensor_data():
    return await get_latest_sensor_data()


@mqtt_router.post("/set_alarm")
async def set_alarm_time(time: int):
    await send_alarm_time(time)
    return {"message": "Alarm time set", "time": time}


@mqtt_router.post("/cancel_alarm")
async def cancel_alarm_time():
    await cancel_alarm()
    return {"message": "Alarm canceled", "time": -1}


@mqtt_router.get("/sleep_rating_status")
async def get_sleep_rating_status():
    can_rate = await check_if_can_rate_sleep_today()
    return {"canRate": can_rate}


@mqtt_router.get("/alarm_status")
async def alarm_status():
    status = await get_alarm_status()
    return status
