import sqlite3
import sys


def search_foods(cursor, query):
    print()
    print("=" * 80)
    print(f"FOODS MATCHING: {query}")
    print("=" * 80)

    rows = cursor.execute(
        """
        SELECT
            f.fdc_id,
            f.data_type,
            f.description
        FROM food_search fs
        JOIN food f
            ON f.fdc_id = fs.rowid
        WHERE food_search MATCH ?
        LIMIT 10
        """,
        (query,),
    ).fetchall()

    if not rows:
        print("No matches.")
        return []

    for index, (fdc_id, data_type, description) in enumerate(rows, start=1):
        print(f"\n[{index}]")
        print(f"FDC ID: {fdc_id}")
        print(f"Type:   {data_type}")
        print(f"Food:   {description}")

    return rows


def show_food(cursor, fdc_id):
    food = cursor.execute(
        """
        SELECT
            fdc_id,
            data_type,
            description,
            food_category_id
        FROM food
        WHERE fdc_id = ?
        """,
        (fdc_id,),
    ).fetchone()

    if food is None:
        print("Food not found.")
        return

    print()
    print("=" * 80)
    print("SELECTED FOOD")
    print("=" * 80)

    print(f"FDC ID:      {food[0]}")
    print(f"Data type:   {food[1]}")
    print(f"Description: {food[2]}")
    print(f"Category ID:  {food[3]}")

    print()
    print("PORTIONS")
    print("-" * 80)

    portions = cursor.execute(
        """
        SELECT
            fp.amount,
            mu.name,
            fp.portion_description,
            fp.modifier,
            fp.gram_weight
        FROM food_portion fp
        LEFT JOIN measure_unit mu
            ON mu.id = fp.measure_unit_id
        WHERE fp.fdc_id = ?
        ORDER BY fp.id
        """,
        (fdc_id,),
    ).fetchall()

    if not portions:
        print("No portions available.")
    else:
        for amount, unit, description, modifier, grams in portions:
            print(
                f"{amount} {unit or ''}"
                f" | {description or ''}"
                f" | modifier={modifier or ''}"
                f" | {grams} g"
            )

    print()
    print("NUTRIENTS")
    print("-" * 80)

    nutrients = cursor.execute(
        """
        SELECT
            n.name,
            fn.amount,
            n.unit_name
        FROM food_nutrient fn
        JOIN nutrient n
            ON n.id = fn.nutrient_id
        WHERE fn.fdc_id = ?
        ORDER BY n.rank
        """,
        (fdc_id,),
    ).fetchall()

    if not nutrients:
        print("No nutrients available.")
    else:
        for name, amount, unit in nutrients:
            print(
                f"{name}: {amount} {unit}"
            )


def database_summary(cursor):
    print("=" * 80)
    print("FRIDAY FOOD DATABASE")
    print("=" * 80)

    tables = [
        "food",
        "nutrient",
        "food_nutrient",
        "food_portion",
        "measure_unit",
        "food_category",
    ]

    for table in tables:
        count = cursor.execute(
            f"SELECT COUNT(*) FROM {table}"
        ).fetchone()[0]

        print(f"{table:20} {count:,}")

    print()


def main():
    database_path = (
        sys.argv[1]
        if len(sys.argv) > 1
        else "friday_food.db"
    )

    connection = sqlite3.connect(database_path)

    try:
        cursor = connection.cursor()

        database_summary(cursor)

        for query in [
            "egg",
            "orange",
            "rice",
            "chicken",
        ]:
            search_foods(cursor, query)

        print()
        print("=" * 80)
        print("Enter an FDC ID to inspect a food.")
        print("Press Enter to finish.")
        print("=" * 80)

        while True:
            value = input("\nFDC ID: ").strip()

            if not value:
                break

            try:
                fdc_id = int(value)
            except ValueError:
                print("Enter a numeric FDC ID.")
                continue

            show_food(cursor, fdc_id)

    finally:
        connection.close()


if __name__ == "__main__":
    main()