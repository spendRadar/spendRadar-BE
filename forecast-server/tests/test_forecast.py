from datetime import date, timedelta
from unittest.mock import Mock

import pandas as pd
import pytest
from fastapi.testclient import TestClient

import engine
from app import app
from backtest import evaluate, prepare


@pytest.fixture(autouse=True)
def no_real_model(monkeypatch):
    monkeypatch.delenv("CHRONOS_API_TOKEN", raising=False)
    monkeypatch.setattr(engine, "_pipeline", Mock())


def payload():
    start = date(2026, 1, 1)
    return {"series": [{"id": "FOOD", "history": [
        {"date": (start + timedelta(days=i)).isoformat(), "amount": 100}
        for i in range(90)]}], "start": "2026-04-01", "days": 2}


def test_forecast_uses_actual_pipeline_and_clips_negative_output():
    engine._pipeline.predict_df.return_value = pd.DataFrame([
        {"id": "FOOD", "date": "2026-04-01", "predictions": -10},
        {"id": "FOOD", "date": "2026-04-02", "predictions": 123.6},
    ])
    response = TestClient(app).post("/forecast", json=payload())
    assert response.status_code == 200
    assert [point["amount"] for point in response.json()["predictions"]] == [0, 124]
    assert engine._pipeline.predict_df.call_args.kwargs["cross_learning"] is False


def test_bridge_day_is_predicted_but_not_returned():
    request = payload()
    request["start"] = "2026-04-02"
    engine._pipeline.predict_df.return_value = pd.DataFrame([
        {"id": "FOOD", "date": f"2026-04-0{day}", "predictions": day * 100}
        for day in (1, 2, 3)
    ])
    response = TestClient(app).post("/forecast", json=request)
    assert response.status_code == 200
    assert len(response.json()["predictions"]) == 2
    assert response.json()["predictions"][0]["date"] == "2026-04-02"
    assert engine._pipeline.predict_df.call_args.kwargs["prediction_length"] == 3


@pytest.mark.parametrize("case", ["gap", "duplicate", "overlap", "fraction", "future_gap"])
def test_rejects_invalid_history_before_model(case):
    request = payload()
    if case == "gap":
        request["series"][0]["history"][40]["date"] = "2026-02-12"
    elif case == "duplicate":
        request["series"].append(request["series"][0])
    elif case == "overlap":
        request["start"] = "2026-03-30"
    elif case == "fraction":
        request["series"][0]["history"][0]["amount"] = 0.5
    else:
        request["start"] = "2027-01-01"
    assert TestClient(app).post("/forecast", json=request).status_code == 422
    engine._pipeline.predict_df.assert_not_called()


def test_service_token_and_model_failure(monkeypatch):
    monkeypatch.setenv("CHRONOS_API_TOKEN", "test-only")
    client = TestClient(app)
    assert client.post("/forecast", json=payload()).status_code == 401
    engine._pipeline.predict_df.side_effect = RuntimeError("private diagnostic")
    response = client.post("/forecast", json=payload(), headers={"Authorization": "Bearer test-only"})
    assert response.status_code == 503
    assert "private diagnostic" not in response.text


def test_backtest_keeps_holdout_out_of_model_and_compares_weekly_baseline(monkeypatch):
    frame = pd.DataFrame({"id": ["FOOD"] * 120, "date": pd.date_range("2026-01-01", periods=120),
                          "amount": [100] * 90 + [200] * 30})
    def fake_forecast(training, start, days):
        assert training["date"].max().date() < start
        assert len(training) == 90
        return [{"id": "FOOD", "date": (start + timedelta(days=i)).isoformat(), "amount": 150}
                for i in range(days)]
    monkeypatch.setattr(engine, "forecast_frame", fake_forecast)
    report = evaluate(frame, 30)
    assert report["categories"]["FOOD"]["chronos"]["mae"] == 50
    assert report["categories"]["FOOD"]["seasonalNaive7"]["mae"] == 100
    with pytest.raises(ValueError):
        prepare(frame.drop(index=100), 30)
