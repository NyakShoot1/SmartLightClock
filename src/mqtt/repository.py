import asyncio
import json

import aiomqtt
from fastapi import HTTPException

from src.core.config import MQTT_ALARM_TIME_TOPIC, MQTT_SENSOR_TOPIC, MQTT_BROKER, MQTT_PORT, MQTT_ALARM_STATUS_TOPIC


async def send_alarm_time(time_in_seconds: int):
    """Отправка времени будильника в MQTT"""
    async with aiomqtt.Client(MQTT_BROKER, MQTT_PORT) as client:
        await client.publish(MQTT_ALARM_TIME_TOPIC, str(time_in_seconds))


async def get_latest_sensor_data():
    """Получение последних данных с датчика"""
    result = {}
    event = asyncio.Event()

    async with aiomqtt.Client(MQTT_BROKER, MQTT_PORT) as client:
        await client.subscribe(MQTT_SENSOR_TOPIC)

        async for message in client.messages:
            try:
                payload = json.loads(message.payload.decode())  # Парсим JSON
                result = {"temperature": payload["temperature"], "humidity": payload["humidity"]}
                event.set()
                break  # Получили данные, можно выходить
            except json.JSONDecodeError:
                print("Ошибка декодирования JSON")

    await event.wait()  # Ждём получения данных

    if not result:
        raise HTTPException(status_code=404, detail="No sensor data received")

    return result


async def listen_to_alarm_status():
    """Слушает MQTT-топик alarm/status и обрабатывает отключение будильника"""
    async with aiomqtt.Client(MQTT_BROKER, MQTT_PORT) as client:
        await client.subscribe(MQTT_ALARM_STATUS_TOPIC)

        async for message in client.messages:
            payload = message.payload.decode()
            print(f"Received message: {payload}")

            if payload == "off":
                print("OFF")
