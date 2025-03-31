from pydantic import BaseModel


class SensorsData(BaseModel):
    temperature: float
    humidity: float
