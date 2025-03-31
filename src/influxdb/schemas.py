from pydantic import BaseModel


class SleepData(BaseModel):
    date: str
    avg_temperature: float
    avg_humidity: float
    sleep_rating: int


class SleepQualityRequest(BaseModel):
    rating: int
