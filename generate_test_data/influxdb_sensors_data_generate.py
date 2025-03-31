from influxdb_client import InfluxDBClient, Point, WritePrecision, Bucket
from influxdb_client.client.write_api import SYNCHRONOUS
import datetime
import random
import time

# Настройки InfluxDB
url = "http://localhost:8086"
token = "L9nloLnC_Lw-8DDeG2M-5AlI2d9_aBSxDwIEMoTL4vQLlx1mtM02d7Ze28yeOh28_Xh_TOVKIkQOeJw5r4czlQ=="
org = "myorg"
bucket = "smart_alarm_clock"

# Создаем клиент InfluxDB
client = InfluxDBClient(url=url, token=token, org=org)
write_api = client.write_api(write_options=SYNCHRONOUS)


# Функция для отправки данных
def send_sensor_data(temperature, humidity, timestamp):
    point = Point("sensors") \
        .field("temperature", temperature) \
        .field("humidity", humidity) \
        .time(timestamp, WritePrecision.NS)

    write_api.write(bucket=bucket, org=org, record=point)
    print(f"Data sent to InfluxDB: {point}")


# Генерация тестовых данных на месяц
def generate_test_data():
    start_time = datetime.datetime(2025, 3, 2, 0, 0, 0)
    end_time = datetime.datetime(2025, 3, 26, 23, 59, 59)
    current_time = start_time

    while current_time <= end_time:
        temperature = round(random.uniform(18.0, 40.0), 1)
        humidity = round(random.uniform(30.0, 80.0), 1)
        send_sensor_data(temperature, humidity, current_time)
        current_time += datetime.timedelta(hours=1)
        time.sleep(0.1)  # Задержка для имитации реального времени


# Пример использования
generate_test_data()
