import asyncio
import json
import sqlite3
from datetime import datetime
import time

import aiomqtt
from fastapi import HTTPException

from src.core.config import MQTT_ALARM_TIME_TOPIC, MQTT_SENSOR_TOPIC, MQTT_BROKER, MQTT_PORT, MQTT_ALARM_STATUS_TOPIC

# Глобальная переменная для хранения состояния будильника
alarm_status = {"active": False, "triggered": False}

async def send_alarm_time(time_in_seconds: int):
    """Отправка времени будильника в MQTT"""
    global alarm_status
    
    if time_in_seconds > 0:
        alarm_status["active"] = True
        alarm_status["triggered"] = False
    else:
        alarm_status["active"] = False
        alarm_status["triggered"] = False
        
    async with aiomqtt.Client(MQTT_BROKER, MQTT_PORT) as client:
        await client.publish(MQTT_ALARM_TIME_TOPIC, str(time_in_seconds))


async def cancel_alarm():
    """Отмена будильника через MQTT"""
    global alarm_status
    alarm_status["active"] = False
    alarm_status["triggered"] = False
    
    async with aiomqtt.Client(MQTT_BROKER, MQTT_PORT) as client:
        # Отправляем специальное значение 0, которое Arduino будет интерпретировать как команду отмены
        await client.publish(MQTT_ALARM_TIME_TOPIC, "0")


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
    global alarm_status
    async with aiomqtt.Client(MQTT_BROKER, MQTT_PORT) as client:
        await client.subscribe(MQTT_ALARM_STATUS_TOPIC)
        
        async for message in client.messages:
            try:
                payload = message.payload.decode()
                print(f"Received message: {payload}")
                
                try:
                    status_data = json.loads(payload)
                    # Обновляем статус будильника
                    alarm_status["active"] = status_data.get("active", False)
                    
                    # Проверяем, сработал ли будильник (если triggered=True)
                    if status_data.get("triggered", False):
                        alarm_status["triggered"] = True
                        print(f"Alarm was triggered: {status_data}")
                        
                        # Запускаем асинхронную задачу для сброса статуса через 10 секунд
                        asyncio.create_task(reset_triggered_status())
                except json.JSONDecodeError:
                    print(f"Error decoding JSON: {payload}")
            except Exception as e:
                print(f"Error processing alarm status message: {e}")


async def reset_triggered_status():
    """Сбрасывает статус triggered через 10 секунд"""
    global alarm_status
    await asyncio.sleep(10)
    alarm_status["triggered"] = False
    print("Reset triggered status to False")


async def get_alarm_status():
    """Возвращает текущий статус будильника"""
    global alarm_status
    return alarm_status


async def check_if_can_rate_sleep_today():
    """Проверяет, можно ли оценить сон сегодня"""
    date = datetime.now().strftime("%Y-%m-%d")
    conn = sqlite3.connect("sleep_data.db")
    cursor = conn.cursor()
    
    # Создаем таблицу, если не существует
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS alarm_status (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT,
            status BOOLEAN,
            rated BOOLEAN
        )
    ''')
    
    # Проверяем, был ли сегодня будильник и была ли оценка
    cursor.execute(
        "SELECT rated FROM alarm_status WHERE date = ? ORDER BY id DESC LIMIT 1", 
        (date,)
    )
    result = cursor.fetchone()
    
    conn.close()
    
    # Если нет записи или rated=False, то можно оценить сон
    if result is None or result[0] == 0:
        return True
    return False


def save_alarm_status(date: str, status: bool, rated: bool):
    conn = sqlite3.connect("sleep_data.db")
    cursor = conn.cursor()

    cursor.execute('''
        CREATE TABLE IF NOT EXISTS alarm_status (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT,
            status BOOLEAN,
            rated BOOLEAN
        )
    ''')

    cursor.execute(
        "INSERT INTO alarm_status (date, status, rated) VALUES (?, ?, ?)",
        (date, status, rated)
    )

    conn.commit()
    conn.close()


def handle_alarm_off():
    date = datetime.now().strftime("%Y-%m-%d")
    save_alarm_status(date, False, False)
    return {"message": "Alarm status saved"}
