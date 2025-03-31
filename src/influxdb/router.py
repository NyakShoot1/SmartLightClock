from datetime import datetime
from typing import Optional

from fastapi import APIRouter, HTTPException, Query

from src.influxdb.repository import get_monthly_sleep_data, send_sleep_quality

sleep_router = APIRouter(prefix="/sleep", tags=["Sleep data"])


@sleep_router.get("/monthly")
async def get_sleep_monthly_data(
        year: Optional[int] = Query(None, description="Year for sleep data (defaults to current year)"),
        month: Optional[int] = Query(None, description="Month for sleep data (defaults to current month)")
):
    try:
        now = datetime.now()
        target_year = year if year is not None else now.year
        target_month = month if month is not None else now.month

        # Validate month
        if not (1 <= target_month <= 12):
            raise HTTPException(status_code=400, detail="Month must be between 1 and 12")

        data = get_monthly_sleep_data(target_year, target_month)
        return {"data": data}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@sleep_router.post("/sleep_quality")
async def receive_sleep_quality(rating: int):
    try:
        if not (1 <= rating <= 10):
            raise HTTPException(status_code=400, detail="Rating must be between 1 and 10")

        send_sleep_quality(rating)
        return {"message": "Sleep quality recorded successfully"}

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
