"""Generate reproducible, explicitly synthetic daily spending; never modify a live DB."""

import argparse
import calendar
import csv
import json
import random
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATEGORIES = ("FOOD", "TRANSPORT", "SHOPPING", "HOUSING", "HEALTH", "LEISURE", "OTHER")
BASE = dict(FOOD=12500, TRANSPORT=3800, SHOPPING=5500, HOUSING=1000,
            HEALTH=1800, LEISURE=4200, OTHER=1500)
PROBABILITY = dict(FOOD=.96, TRANSPORT=.76, SHOPPING=.23, HOUSING=.10,
                   HEALTH=.09, LEISURE=.34, OTHER=.20)


def sql_string(value):
    return "'" + str(value).replace("'", "''") + "'"


def month_factor(category, month):
    factors = {
        "FOOD": {3: 1.65, 12: 1.20},
        "TRANSPORT": {7: .88, 8: .85},
        "SHOPPING": {3: 1.25, 10: 1.50, 11: 1.65},
        "HOUSING": {1: 1.25, 8: 1.25},
        "HEALTH": {1: 1.15, 2: 1.15},
        "LEISURE": {7: 1.75, 8: 1.90, 1: .75, 2: .75},
        "OTHER": {12: 1.30},
    }
    return factors[category].get(month, 1.0)


def generate(end, seed=20261015):
    # Exactly 3 calendar years, including leap days; end is exclusive.
    start = end.replace(year=end.year - 3, day=min(end.day, calendar.monthrange(end.year - 3, end.month)[1]))
    rng = random.Random(seed)
    daily, transactions = [], []
    monthly_noise = {}
    month_totals = defaultdict(lambda: defaultdict(int))
    recent_start = end - timedelta(days=180)
    days = (end - start).days

    def transaction(day, amount, category, kind, label, entry_type="EXPENSE"):
        row = {"date": day.isoformat(), "amount": int(amount), "category": category,
               "type": entry_type, "expenseKind": kind,
               "description": f"[SYNTHETIC] {label} {day.isoformat()}"}
        transactions.append(row)
        if entry_type == "EXPENSE":
            month_totals[day.strftime("%Y-%m")][category] += int(amount)

    for index in range(days):
        day = start + timedelta(days=index)
        for category in CATEGORIES:
            key = (day.year, day.month, category)
            if key not in monthly_noise:
                monthly_noise[key] = rng.uniform(.90, 1.10)
            weekend = day.weekday() >= 5
            weekly = 1.18 if weekend and category in ("FOOD", "SHOPPING", "LEISURE") else 1.0
            if category == "TRANSPORT" and weekend:
                weekly = .55
            # Slow drift plus an independent recent change, not just a calendar template.
            trend = 1 + .08 * index / 365.25
            if category == "FOOD" and day >= recent_start:
                trend *= 1 + .20 * (day - recent_start).days / 180
            if category == "TRANSPORT" and day >= recent_start:
                trend *= .78
            seasonal = month_factor(category, day.month)
            amount = 0
            # Seasonal variation changes ticket size, with zero-spend days retained.
            if rng.random() < PROBABILITY[category]:
                value = BASE[category] / PROBABILITY[category]
                value *= seasonal * weekly * trend * monthly_noise[key] * rng.lognormvariate(-.045, .30)
                amount = max(100, round(value / 100) * 100)
            # One isolated event, deliberately not a repeating annual signal.
            if category == "HEALTH" and day == end - timedelta(days=110):
                amount += 240000
            daily.append({"id": category, "date": day.isoformat(), "amount": amount})
            if amount:
                transaction(day, amount, category, "VARIABLE", f"변동지출 {category}")

        if day.day == 25:
            transaction(day, round((2800000 + 100000 * index / 365.25) / 10000) * 10000,
                        "SALARY", None, "월급", "INCOME")
        for payment_day, amount, category, label in (
            (1, 650000, "HOUSING", "월세"),
            (10, 55000, "OTHER", "통신비"),
            (15, 25000, "LEISURE", "정기구독"),
        ):
            if day.day == payment_day:
                transaction(day, amount, category, "FIXED", label)

    # Keep regular settings consistent with the last generated salary.
    salary = [row["amount"] for row in transactions if row["type"] == "INCOME"][-1]
    recurring_income = {"name": "[SYNTHETIC] 월급", "amount": salary, "paymentDay": 25, "active": True}
    fixed_expenses = [
        {"name": "[SYNTHETIC] 월세", "amount": 650000, "paymentDay": 1, "active": True, "category": "HOUSING"},
        {"name": "[SYNTHETIC] 통신비", "amount": 55000, "paymentDay": 10, "active": True, "category": "OTHER"},
        {"name": "[SYNTHETIC] 정기구독", "amount": 25000, "paymentDay": 15, "active": True, "category": "LEISURE"},
    ]
    metadata = {"synthetic": True, "seed": seed, "historyStart": start.isoformat(),
                "endExclusive": end.isoformat(), "actualThrough": (end - timedelta(days=1)).isoformat(),
                "days": days, "transactionCount": len(transactions), "categories": list(CATEGORIES),
                "patterns": ["3월 식비 배율 1.65", "7~8월 여가비 증가", "10~11월 쇼핑 증가",
                             "주말 식비·여가비 증가 및 교통비 감소", "연 8% 완만한 지출 증가",
                             "최근 180일 식비 추가 증가·교통비 감소", "1회성 건강 지출 및 확률적 무지출일"],
                "warning": "시연 전용 합성 자료. 실제 사용자 기록이나 실제 예측 성능의 근거가 아님."}
    return transactions, daily, recurring_income, fixed_expenses, metadata, month_totals


def write_files(output, end, seed):
    rows, daily, income, fixed, metadata, monthly = generate(end, seed)
    output.mkdir(parents=True, exist_ok=True)
    (output / "transactions.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    (output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    (output / "recurring.json").write_text(json.dumps({"income": income, "fixedExpenses": fixed},
                                                      ensure_ascii=False, indent=2), encoding="utf-8")
    with (output / "variable-daily.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=("id", "date", "amount"))
        writer.writeheader()
        writer.writerows(daily)
    with (output / "monthly-summary.csv").open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=("month", *CATEGORIES, "total"))
        writer.writeheader()
        for month, amounts in sorted(monthly.items()):
            writer.writerow({"month": month, **{key: amounts[key] for key in CATEGORIES}, "total": sum(amounts.values())})
    sql = ["-- SYNTHETIC ONLY. Load via the dedicated synthetic profile; never on personal data."]
    for row in rows:
        values = [sql_string(row["date"]), str(row["amount"]), sql_string(row["category"]),
                  sql_string(row["type"]), sql_string(row["expenseKind"]) if row["expenseKind"] else "NULL",
                  sql_string(row["description"])]
        sql.append("INSERT INTO ledger (entry_date, amount, category, type, expense_kind, description) SELECT "
                   + ", ".join(values) + " WHERE NOT EXISTS (SELECT 1 FROM ledger WHERE description = "
                   + sql_string(row["description"]) + ");")
    for table, item in [("recurring_income", income), *[("fixed_expense", item) for item in fixed]]:
        columns = "name, amount, payment_day, active"
        values = f"{sql_string(item['name'])}, {item['amount']}, {item['paymentDay']}, TRUE"
        if table == "fixed_expense":
            columns += ", category"
            values += ", " + sql_string(item["category"])
        sql.append(f"INSERT INTO {table} ({columns}) SELECT {values} WHERE NOT EXISTS "
                   f"(SELECT 1 FROM {table} WHERE name = {sql_string(item['name'])});")
    (output / "seed.sql").write_text("\n".join(sql) + "\n", encoding="utf-8")
    # Validate essential quality invariants before advertising this output as complete.
    assert len(daily) == metadata["days"] * len(CATEGORIES)
    assert len({(row["id"], row["date"]) for row in daily}) == len(daily)
    assert all(row["amount"] > 0 and row["description"].startswith("[SYNTHETIC]") for row in rows)
    assert sum(row["amount"] for row in daily) == sum(row["amount"] for row in rows if row["expenseKind"] == "VARIABLE")
    print(json.dumps(metadata, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--end", type=date.fromisoformat, default=datetime.now(timezone(timedelta(hours=9))).date(),
                        help="Exclusive history end, usually today in Seoul")
    parser.add_argument("--seed", type=int, default=20261015)
    parser.add_argument("--output", type=Path, default=ROOT / "data" / "synthetic")
    args = parser.parse_args()
    write_files(args.output, args.end, args.seed)

