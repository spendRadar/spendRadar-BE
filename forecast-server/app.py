import hmac
import logging
import os
from datetime import date, timedelta
from typing import Annotated

import pandas as pd
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field, model_validator

import engine

app = FastAPI(title="AI Ledger Chronos-2", version="1.0.0")
logger = logging.getLogger(__name__)


class Observation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    date: date
    amount: int = Field(ge=0, le=1_000_000_000_000, strict=True)


class Series(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(pattern=r"^[A-Z_]{1,40}$")
    history: list[Observation] = Field(min_length=90, max_length=1830)


class ForecastRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    series: list[Series] = Field(min_length=1, max_length=8)
    start: date
    days: int = Field(ge=1, le=62, strict=True)

    @model_validator(mode="after")
    def validate_history(self):
        ids = [item.id for item in self.series]
        if len(set(ids)) != len(ids):
            raise ValueError("Duplicate series id")
        expected_dates = [row.date for row in self.series[0].history]
        if any(b != a + timedelta(days=1) for a, b in zip(expected_dates, expected_dates[1:])):
            raise ValueError("History must contain consecutive daily observations")
        if any([row.date for row in item.history] != expected_dates for item in self.series):
            raise ValueError("Series must have matching dates")
        horizon = (self.start - expected_dates[-1]).days - 1 + self.days
        if self.start <= expected_dates[-1] or horizon > 93:
            raise ValueError("Forecast must follow history, within 93 days of history end")
        return self


def authorize(authorization: Annotated[str | None, Header()] = None):
    token = os.getenv("CHRONOS_API_TOKEN", "")
    if token and not hmac.compare_digest(authorization or "", "Bearer " + token):
        raise HTTPException(401, detail="Invalid service token")


@app.get("/health")
def health():
    return {"status": "ok", "modelId": engine.MODEL_ID, "modelLoaded": engine.loaded()}


@app.post("/forecast", dependencies=[Depends(authorize)])
def forecast(request: ForecastRequest):
    frame = pd.DataFrame([
        {"id": item.id, "date": pd.Timestamp(row.date), "amount": row.amount}
        for item in request.series for row in item.history
    ])
    try:
        return {"modelId": engine.MODEL_ID,
                "predictions": engine.forecast_frame(frame, request.start, request.days)}
    except Exception as error:
        # Log only the exception class: remote errors can contain local paths or request data.
        logger.error("Chronos failed (%s)", type(error).__name__)
        raise HTTPException(503, detail="Chronos inference unavailable; check model installation") from error
