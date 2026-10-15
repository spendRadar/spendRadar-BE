"""Chronos-2 adapter. No heuristic or synthetic fallback in production."""

import math
import os
import threading
from datetime import date

import pandas as pd

MODEL_ID = "amazon/chronos-2"
_pipeline = None
_lock = threading.Lock()


def loaded():
    return _pipeline is not None


def forecast_frame(frame, start: date, days: int):
    """Input must contain aligned, consecutive daily id/date/amount observations."""
    global _pipeline
    last = pd.Timestamp(frame["date"].max()).date()
    offset = (start - last).days - 1
    if offset < 0 or offset + days > 93:
        raise ValueError("Forecast window must follow history and fit within 93 days")
    with _lock:
        if _pipeline is None:
            from chronos import Chronos2Pipeline

            _pipeline = Chronos2Pipeline.from_pretrained(
                MODEL_ID, device_map=os.getenv("CHRONOS_DEVICE", "cpu")
            )
        output = _pipeline.predict_df(
            frame,
            id_column="id",
            timestamp_column="date",
            target="amount",
            prediction_length=offset + days,
            quantile_levels=[0.5],
            cross_learning=False,
            freq="D",
        )

    # Chronos' `predictions` column is its point median. These are not monthly quantiles.
    expected = {(str(item), start + pd.Timedelta(days=i))
                for item in frame["id"].unique() for i in range(days)}
    points = []
    seen = set()
    for row in output.to_dict("records"):
        day = pd.Timestamp(row["date"]).date()
        key = (str(row["id"]), day)
        if key not in expected:
            continue
        value = float(row["predictions"])
        if key in seen or not math.isfinite(value) or value > 1_000_000_000_000:
            raise ValueError("Invalid model prediction")
        seen.add(key)
        points.append({"id": key[0], "date": day.isoformat(), "amount": max(0, round(value))})
    if seen != expected:
        raise ValueError("Incomplete model prediction")
    return sorted(points, key=lambda point: (point["id"], point["date"]))
