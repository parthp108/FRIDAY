import argparse
import csv
import io
import os
import sqlite3
import zipfile
from collections import Counter


CORE_DATA_TYPES = {
    "foundation_food",
    "sr_legacy_food",
    "survey_fndds_food",
}

BATCH_SIZE = 5000


SCHEMA = """
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = OFF;

DROP TABLE IF EXISTS food;
DROP TABLE IF EXISTS nutrient;
DROP TABLE IF EXISTS food_nutrient;
DROP TABLE IF EXISTS measure_unit;
DROP TABLE IF EXISTS food_portion;
DROP TABLE IF EXISTS food_category;
DROP TABLE IF EXISTS food_search;

CREATE TABLE food (
    fdc_id INTEGER PRIMARY KEY,
    data_type TEXT NOT NULL,
    description TEXT NOT NULL,
    food_category_id INTEGER,
    publication_date TEXT
);

CREATE TABLE nutrient (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    unit_name TEXT NOT NULL,
    nutrient_nbr TEXT,
    rank REAL
);

CREATE TABLE food_nutrient (
    id INTEGER PRIMARY KEY,
    fdc_id INTEGER NOT NULL,
    nutrient_id INTEGER NOT NULL,
    amount REAL,
    data_points INTEGER,
    derivation_id INTEGER,
    min REAL,
    max REAL,
    median REAL,
    loq REAL,
    footnote TEXT,
    percent_daily_value REAL
);

CREATE TABLE measure_unit (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE food_portion (
    id INTEGER PRIMARY KEY,
    fdc_id INTEGER NOT NULL,
    seq_num INTEGER,
    amount REAL,
    measure_unit_id INTEGER,
    portion_description TEXT,
    modifier TEXT,
    gram_weight REAL
);

CREATE TABLE food_category (
    id INTEGER PRIMARY KEY,
    code TEXT,
    description TEXT NOT NULL
);

CREATE VIRTUAL TABLE food_search USING fts5(
    description,
    content='food',
    content_rowid='fdc_id'
);

CREATE INDEX idx_food_data_type
    ON food(data_type);

CREATE INDEX idx_food_category
    ON food(food_category_id);

CREATE INDEX idx_food_nutrient_fdc
    ON food_nutrient(fdc_id);

CREATE INDEX idx_food_nutrient_nutrient
    ON food_nutrient(nutrient_id);

CREATE INDEX idx_food_portion_fdc
    ON food_portion(fdc_id);

CREATE INDEX idx_food_portion_unit
    ON food_portion(measure_unit_id);
"""


def csv_reader(zip_file, filename):
    """
    Open a CSV inside the USDA ZIP without extracting the entire dataset.
    """
    with zip_file.open(filename) as raw:
        text = io.TextIOWrapper(
            raw,
            encoding="utf-8-sig",
            newline=""
        )

        yield from csv.DictReader(text)


def find_file(zip_file, filename):
    """
    Find a USDA CSV regardless of the parent directory name.
    """
    suffix = "/" + filename

    for name in zip_file.namelist():
        if name.endswith(suffix):
            return name

    raise FileNotFoundError(
        f"Could not find {filename} inside USDA ZIP."
    )


def to_int(value):
    if value is None or value == "":
        return None

    try:
        return int(value)
    except ValueError:
        return None


def to_float(value):
    if value is None or value == "":
        return None

    try:
        return float(value)
    except ValueError:
        return None


def build_food_database(usda_zip, output_db):
    if os.path.exists(output_db):
        print(f"Removing existing database: {output_db}")
        os.remove(output_db)

    print("Opening USDA dataset...")

    with zipfile.ZipFile(usda_zip, "r") as z:

        print("Creating SQLite database...")

        connection = sqlite3.connect(output_db)
        connection.executescript(SCHEMA)

        try:
            build_categories(connection, z)
            build_units(connection, z)
            build_nutrients(connection, z)
            build_foods(connection, z)
            build_portions(connection, z)
            build_food_nutrients(connection, z)
            build_search_index(connection)

            connection.commit()

        finally:
            connection.close()

    print()
    print("========================================")
    print("FRIDAY FOOD DATABASE CREATED")
    print("========================================")
    print(f"Output: {output_db}")
    print()


def build_categories(connection, z):
    filename = find_file(z, "food_category.csv")

    print("Importing food categories...")

    rows = []

    for row in csv_reader(z, filename):
        rows.append(
            (
                to_int(row["id"]),
                row["code"],
                row["description"],
            )
        )

    connection.executemany(
        """
        INSERT INTO food_category (
            id,
            code,
            description
        )
        VALUES (?, ?, ?)
        """,
        rows,
    )

    connection.commit()


def build_units(connection, z):
    filename = find_file(z, "measure_unit.csv")

    print("Importing measurement units...")

    rows = []

    for row in csv_reader(z, filename):
        rows.append(
            (
                to_int(row["id"]),
                row["name"],
            )
        )

    connection.executemany(
        """
        INSERT INTO measure_unit (
            id,
            name
        )
        VALUES (?, ?)
        """,
        rows,
    )

    connection.commit()


def build_nutrients(connection, z):
    filename = find_file(z, "nutrient.csv")

    print("Importing nutrient definitions...")

    rows = []

    for row in csv_reader(z, filename):
        rows.append(
            (
                to_int(row["id"]),
                row["name"],
                row["unit_name"],
                row["nutrient_nbr"],
                to_float(row["rank"]),
            )
        )

    connection.executemany(
        """
        INSERT INTO nutrient (
            id,
            name,
            unit_name,
            nutrient_nbr,
            rank
        )
        VALUES (?, ?, ?, ?, ?)
        """,
        rows,
    )

    connection.commit()


def build_foods(connection, z):
    filename = find_file(z, "food.csv")

    print("Importing core foods...")

    insert_sql = """
        INSERT INTO food (
            fdc_id,
            data_type,
            description,
            food_category_id,
            publication_date
        )
        VALUES (?, ?, ?, ?, ?)
    """

    rows = []
    counts = Counter()

    for row in csv_reader(z, filename):

        data_type = row["data_type"]

        if data_type not in CORE_DATA_TYPES:
            continue

        fdc_id = to_int(row["fdc_id"])

        if fdc_id is None:
            continue

        rows.append(
            (
                fdc_id,
                data_type,
                row["description"],
                to_int(row["food_category_id"]),
                row["publication_date"],
            )
        )

        counts[data_type] += 1

        if len(rows) >= BATCH_SIZE:
            connection.executemany(insert_sql, rows)
            rows.clear()

    if rows:
        connection.executemany(insert_sql, rows)

    connection.commit()

    print("Foods imported:")

    for data_type, count in sorted(counts.items()):
        print(f"  {data_type}: {count:,}")


def build_portions(connection, z):
    filename = find_file(z, "food_portion.csv")

    print("Importing food portions...")

    insert_sql = """
        INSERT INTO food_portion (
            id,
            fdc_id,
            seq_num,
            amount,
            measure_unit_id,
            portion_description,
            modifier,
            gram_weight
        )
        SELECT ?, ?, ?, ?, ?, ?, ?, ?
        WHERE EXISTS (
            SELECT 1
            FROM food
            WHERE food.fdc_id = ?
        )
    """

    rows = []

    for row in csv_reader(z, filename):

        fdc_id = to_int(row["fdc_id"])

        if fdc_id is None:
            continue

        rows.append(
            (
                to_int(row["id"]),
                fdc_id,
                to_int(row["seq_num"]),
                to_float(row["amount"]),
                to_int(row["measure_unit_id"]),
                row["portion_description"],
                row["modifier"],
                to_float(row["gram_weight"]),
                fdc_id,
            )
        )

        if len(rows) >= BATCH_SIZE:
            connection.executemany(insert_sql, rows)
            rows.clear()

    if rows:
        connection.executemany(insert_sql, rows)

    connection.commit()


def build_food_nutrients(connection, z):
    filename = find_file(z, "food_nutrient.csv")

    print("Importing food nutrients...")
    print("This is the largest CSV and will take the longest.")

    insert_sql = """
        INSERT INTO food_nutrient (
            id,
            fdc_id,
            nutrient_id,
            amount,
            data_points,
            derivation_id,
            min,
            max,
            median,
            loq,
            footnote,
            percent_daily_value
        )
        SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
        WHERE EXISTS (
            SELECT 1
            FROM food
            WHERE food.fdc_id = ?
        )
    """

    rows = []

    processed = 0
    inserted = 0

    for row in csv_reader(z, filename):

        fdc_id = to_int(row["fdc_id"])

        if fdc_id is None:
            continue

        processed += 1

        rows.append(
            (
                to_int(row["id"]),
                fdc_id,
                to_int(row["nutrient_id"]),
                to_float(row["amount"]),
                to_int(row["data_points"]),
                to_int(row["derivation_id"]),
                to_float(row["min"]),
                to_float(row["max"]),
                to_float(row["median"]),
                to_float(row["loq"]),
                row["footnote"],
                to_float(row["percent_daily_value"]),
                fdc_id,
            )
        )

        if len(rows) >= BATCH_SIZE:
            connection.executemany(insert_sql, rows)
            inserted += len(rows)
            rows.clear()

            if processed % 1000000 < BATCH_SIZE:
                print(
                    f"  processed {processed:,} nutrient rows..."
                )

    if rows:
        connection.executemany(insert_sql, rows)
        inserted += len(rows)

    connection.commit()

    print(
        f"  processed {processed:,} nutrient rows"
    )


def build_search_index(connection):
    print("Building food search index...")

    connection.execute(
        """
        INSERT INTO food_search (
            rowid,
            description
        )
        SELECT
            fdc_id,
            description
        FROM food
        """
    )

    connection.commit()


def main():
    parser = argparse.ArgumentParser(
        description="Build FRIDAY's compact USDA food database."
    )

    parser.add_argument(
        "usda_zip",
        help="Path to FoodData_Central_csv_2026-04-30.zip"
    )

    parser.add_argument(
        "-o",
        "--output",
        default="friday_food.db",
        help="Output SQLite database path"
    )

    args = parser.parse_args()

    if not os.path.isfile(args.usda_zip):
        raise SystemExit(
            f"USDA ZIP not found: {args.usda_zip}"
        )

    build_food_database(
        args.usda_zip,
        args.output
    )


if __name__ == "__main__":
    main()