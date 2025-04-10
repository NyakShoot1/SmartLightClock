import sqlite3
from datetime import datetime, timedelta
from typing import List, Dict

from fastapi import HTTPException
from influxdb_client import Point
from influxdb_client.client.write_api import ASYNCHRONOUS

from src.core.config import SMART_ALARM_BUCKET, SLEEP_RATING_BUCKET, INFLUXDB_ORG
from src.influxdb.schemas import SleepData
from .client import get_influx_client


def get_monthly_sleep_data(year: int, month: int) -> List[SleepData]:
    client = get_influx_client()
    query_api = client.query_api()

    start_date = datetime(year, month, 1)
    end_date = (datetime(year, month + 1, 1) - timedelta(seconds=1)) if month < 12 else (
            datetime(year + 1, 1, 1) - timedelta(seconds=1))

    start_str, end_str = start_date.isoformat() + "Z", end_date.isoformat() + "Z"

    sensors_query = f'''
    from(bucket: "{SMART_ALARM_BUCKET}")
      |> range(start: {start_str}, stop: {end_str})
      |> filter(fn: (r) => r._field == "temperature" or r._field == "humidity")
      |> aggregateWindow(every: 1d, fn: mean, createEmpty: false)
      |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
    '''

    sleep_query = f'''
    from(bucket: "{SLEEP_RATING_BUCKET}")
      |> range(start: {start_str}, stop: {end_str})
      |> filter(fn: (r) => r._measurement == "sleep_rating" and r._field == "rating")
      |> aggregateWindow(every: 1d, fn: last, createEmpty: false)
    '''
    
    # Запрос для получения времени пробуждения
    wake_time_query = f'''
    from(bucket: "{SMART_ALARM_BUCKET}")
      |> range(start: {start_str}, stop: {end_str})
      |> filter(fn: (r) => r._measurement == "wake_time" and r._field == "wake_duration")
      |> aggregateWindow(every: 1d, fn: last, createEmpty: false)
    '''

    sensors_result = query_api.query(sensors_query)
    sleep_result = query_api.query(sleep_query)
    wake_time_result = query_api.query(wake_time_query)

    sensors_by_day: Dict[str, Dict[str, float]] = {}
    for table in sensors_result:
        for record in table.records:
            day = record["_time"].strftime('%Y-%m-%d')
            sensors_by_day[day] = {
                "temperature": record.values.get("temperature", 0.0) if "temperature" in record.values else 0.0,
                "humidity": record.values.get("humidity", 0.0) if "humidity" in record.values else 0.0
            }

    sleep_by_day: Dict[str, int] = {}
    for table in sleep_result:
        for record in table.records:
            day = record["_time"].strftime('%Y-%m-%d')
            sleep_by_day[day] = int(record.get_value() or 0)
            
    # Собираем времена пробуждения по дням
    wake_time_by_day: Dict[str, int] = {}
    for table in wake_time_result:
        for record in table.records:
            day = record["_time"].strftime('%Y-%m-%d')
            wake_time_by_day[day] = int(record.get_value() or 0)

    result = [
        SleepData(
            date=day,
            avg_temperature=round(sensors_by_day.get(day, {}).get("temperature", 0.0), 1),
            avg_humidity=round(sensors_by_day.get(day, {}).get("humidity", 0.0), 1),
            sleep_rating=sleep_by_day.get(day, 0),
            wake_time=wake_time_by_day.get(day, None)
        )
        for day in sorted(set(sensors_by_day.keys()).union(sleep_by_day.keys()))
    ]

    client.close()
    return result


def send_sleep_quality(rating: int):
    client = get_influx_client()
    write_api = client.write_api(write_options=ASYNCHRONOUS)

    point = Point("sleep_rating") \
        .field("rating", rating)

    write_api.write(bucket=SLEEP_RATING_BUCKET, org=INFLUXDB_ORG, record=point)
    
    # Обновляем статус оценки сна в базе данных
    date = datetime.now().strftime("%Y-%m-%d")
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
    
    # Проверка существования записи за сегодня
    cursor.execute("SELECT id FROM alarm_status WHERE date = ?", (date,))
    result = cursor.fetchone()
    
    if result:
        # Обновляем существующую запись
        cursor.execute("UPDATE alarm_status SET rated = ? WHERE date = ?", (True, date))
    else:
        # Создаем новую запись
        cursor.execute(
            "INSERT INTO alarm_status (date, status, rated) VALUES (?, ?, ?)",
            (date, True, True)
        )
        
    conn.commit()
    conn.close()


def get_daily_sensor_data(year: int, month: int, day: int):
    client = get_influx_client()
    query_api = client.query_api()

    start_date = datetime(year, month, day).isoformat() + "Z"
    end_date = (datetime(year, month, day, 23, 59, 59)).isoformat() + "Z"

    sensors_query = f'''
    from(bucket: "{SMART_ALARM_BUCKET}")
      |> range(start: {start_date}, stop: {end_date})
      |> filter(fn: (r) => r._field == "temperature" or r._field == "humidity")
      |> aggregateWindow(every: 1d, fn: mean, createEmpty: false)
      |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
    '''

    sleep_query = f'''
    from(bucket: "{SLEEP_RATING_BUCKET}")
      |> range(start: {start_date}, stop: {end_date})
      |> filter(fn: (r) => r._measurement == "sleep_rating" and r._field == "rating")
      |> aggregateWindow(every: 1d, fn: last, createEmpty: false)
    '''
    
    # Запрос для получения времени пробуждения
    wake_time_query = f'''
    from(bucket: "{SMART_ALARM_BUCKET}")
      |> range(start: {start_date}, stop: {end_date})
      |> filter(fn: (r) => r._measurement == "wake_time" and r._field == "wake_duration")
      |> aggregateWindow(every: 1d, fn: last, createEmpty: false)
    '''

    sensors_result = query_api.query(sensors_query)
    sleep_result = query_api.query(sleep_query)
    wake_time_result = query_api.query(wake_time_query)
    client.close()

    temperature, humidity, sleep_rating = 0.0, 0.0, 0
    wake_time = None

    for table in sensors_result:
        for record in table.records:
            temperature = record.values.get("temperature", 0.0)
            humidity = record.values.get("humidity", 0.0)

    for table in sleep_result:
        for record in table.records:
            sleep_rating = int(record.get_value() or 0)
            
    for table in wake_time_result:
        for record in table.records:
            wake_time = int(record.get_value() or 0)

    if temperature == 0.0 and humidity == 0.0 and sleep_rating == 0 and wake_time is None:
        raise HTTPException(status_code=404, detail="No data found for the given date")

    return {
        "temperature": temperature,
        "humidity": humidity,
        "sleep_rating": sleep_rating,
        "wake_time": wake_time
    }
