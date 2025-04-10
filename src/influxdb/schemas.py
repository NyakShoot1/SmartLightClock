from pydantic import BaseModel
from typing import Optional


class SleepData(BaseModel):
    date: str
    avg_temperature: float
    avg_humidity: float
    sleep_rating: int
    wake_time: Optional[int] = None  # Время пробуждения в миллисекундах


class SleepQualityRequest(BaseModel):
    rating: int
