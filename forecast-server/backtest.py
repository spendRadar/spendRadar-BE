"""Hold out the last N days. CSV columns: id,date,amount (daily VARIABLE expenses)."""

import argparse
import json
from pathlib import Path

import pandas as pd

import engine
from app import ForecastRequest


def prepare(frame, days):
    frame = frame.copy()
    frame["date"] = pd.to_datetime(frame["date"], errors="raise")
    if not frame["date"].eq(frame["date"].dt.normalize()).all():
        raise ValueError("Daily dates must not have a time component")
    cutoff = frame["date"].max() - pd.Timedelta(days=days - 1)
    training = frame[frame["date"] < cutoff].sort_values(["id", "date"])
    actual = frame[frame["date"] >= cutoff].sort_values(["id", "date"])
    series = []
    for item_id, group in training.groupby("id"):
        series.append({"id": item_id, "history": [
            {"date": row.date.date(), "amount": row.amount}
            for row in group.itertuples(index=False)
        ]})
    ForecastRequest(series=series, start=cutoff.date(), days=days)
    expected = {(item_id, day) for item_id in training["id"].unique()
                for day in pd.date_range(cutoff, periods=days)}
    observed = list(zip(actual["id"], actual["date"]))
    if set(observed) != expected or len(observed) != len(expected):
        raise ValueError("Holdout must have exactly one observation per category per day")
    if (actual["amount"] < 0).any() or (actual["amount"] > 1e12).any() \
            or not actual["amount"].map(lambda x: pd.notna(x) and float(x).is_integer()).all():
        raise ValueError("Invalid holdout amounts")
    return training, actual, cutoff.date()


def metrics(actual, predicted):
    errors = (actual - predicted).abs()
    denominator = actual.abs().sum()
    return {"mae": float(errors.mean()),
            "wape": float(errors.sum() / denominator) if denominator else None}


def evaluate(frame, days):
    training, actual, start = prepare(frame, days)
    points = pd.DataFrame(engine.forecast_frame(training, start, days))
    points["date"] = pd.to_datetime(points["date"])
    merged = actual.merge(points.rename(columns={"amount": "predicted"}), on=["id", "date"], validate="one_to_one")
    baselines = {}
    for item_id, group in training.groupby("id"):
        last_week = group.tail(7)["amount"].tolist()
        for i in range(days):
            baselines[(item_id, pd.Timestamp(start) + pd.Timedelta(days=i))] = last_week[i % 7]
    merged["baseline"] = [baselines[(row.id, row.date)] for row in merged.itertuples()]
    return {"modelId": engine.MODEL_ID, "holdoutStart": start.isoformat(), "days": days,
            "method": "single chronological holdout; weekly seasonal-naive baseline",
            "categories": {item_id: {"chronos": metrics(group.amount, group.predicted),
                                     "seasonalNaive7": metrics(group.amount, group.baseline)}
                           for item_id, group in merged.groupby("id")}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--days", type=int, default=30, choices=range(1, 63))
    parser.add_argument("--output", type=Path, default=Path("backtest-report.json"))
    args = parser.parse_args()
    report = evaluate(pd.read_csv(args.csv), args.days)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Report written to {args.output}")
