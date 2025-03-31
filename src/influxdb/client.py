from influxdb_client import InfluxDBClient
from src.core.config import INFLUXDB_URL, INFLUXDB_TOKEN, INFLUXDB_ORG


def get_influx_client():
    return InfluxDBClient(url=INFLUXDB_URL, token=INFLUXDB_TOKEN, org=INFLUXDB_ORG)
