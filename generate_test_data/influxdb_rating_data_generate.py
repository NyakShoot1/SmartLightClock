from influxdb_client import InfluxDBClient, Point, WritePrecision, Bucket
from influxdb_client.client.write_api import SYNCHRONOUS
import datetime
import random
import time

# Настройки InfluxDB
url = "http://localhost:8086"
token = "L9nloLnC_Lw-8DDeG2M-5AlI2d9_aBSxDwIEMoTL4vQLlx1mtM02d7Ze28yeOh28_Xh_TOVKIkQOeJw5r4czlQ=="
org = "myorg"
bucket = "sleep_rating"

# Создаем клиент InfluxDB
client = InfluxDBClient(url=url, token=token, org=org)
write_api = client.write_api(write_options=SYNCHRONOUS)


# Функция для отправки оценки качества сна
def send_sleep_quality(rating, timestamp):
    point = Point("sleep_rating") \
        .field("rating", rating) \
        .time(timestamp, WritePrecision.NS)

    write_api.write(bucket=bucket, org=org, record=point)
    print(f"Sleep quality data sent to InfluxDB: {point}")


# Генерация тестовых данных для оценки сна
def generate_sleep_quality_data():
    start_time = datetime.datetime(2025, 3, 2, 0, 0, 0)
    end_time = datetime.datetime(2025, 3, 26, 23, 59, 59)
    current_time = start_time

    while current_time <= end_time:
        rating = random.randint(1, 10)
        send_sleep_quality(rating, current_time)
        current_time += datetime.timedelta(days=1)
        time.sleep(0.1)  # Задержка для имитации реального времени


# Пример использования
generate_sleep_quality_data()
