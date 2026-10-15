"""Download/load real Chronos weights and check inference using synthetic data, without touching the DB."""

from datetime import date

import pandas as pd

import engine

if __name__ == "__main__":
    frame = pd.DataFrame({
        "id": ["FOOD"] * 90,
        "date": pd.date_range("2026-01-01", periods=90),
        "amount": [10000 if i % 7 < 5 else 20000 for i in range(90)],
    })
    points = engine.forecast_frame(frame, date(2026, 4, 1), 7)
    assert len(points) == 7
    print(f"Real model inference OK: {engine.MODEL_ID}, {len(points)} daily points")
    print("Synthetic smoke test only; this does NOT establish accuracy on personal spending.")
