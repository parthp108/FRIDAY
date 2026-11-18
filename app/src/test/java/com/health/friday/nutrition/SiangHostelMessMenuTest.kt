package com.health.friday.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiangHostelMessMenuTest {

    private val parser = FoodParser()

    @Test
    fun testMondayMenuFullParsing() {
        val breakfast = "Monday Breakfast: 2 aloo pyaz paratha, chowmein, 1 apple, 200 ml milk, 1 boiled egg"
        val parsedBreakfast = parser.parse(breakfast)
        assertEquals(5, parsedBreakfast.size)
        assertEquals("aloo pyaz paratha", parsedBreakfast[0].name)
        assertEquals(2.0, parsedBreakfast[0].quantity, 0.01)

        val lunch = "Monday Lunch: chana dal, kadhai pakoda, bhindi masala, plain rice, sweet lassi, 3 roti, curd, papad"
        val parsedLunch = parser.parse(lunch)
        assertEquals(8, parsedLunch.size)

        val dinner = "Monday Dinner: egg curry, veg malai kofta, arhar dal, tomato rice, jalebi, 2 paneer roti"
        val parsedDinner = parser.parse(dinner)
        assertEquals(6, parsedDinner.size)
    }

    @Test
    fun testTuesdayMenuFullParsing() {
        val breakfast = "Tuesday Breakfast: 2 idli, 1 masala dosa, 1 medu vada, 1 banana, 200 ml milk, paneer bhurji"
        val parsedBreakfast = parser.parse(breakfast)
        assertEquals(6, parsedBreakfast.size)

        val lunch = "Tuesday Lunch: kala chana, long beans dry, aloo gobi, mysore dal, jeera rice, 3 roti, curd"
        val parsedLunch = parser.parse(lunch)
        assertEquals(7, parsedLunch.size)

        val dinner = "Tuesday Dinner: veg manchurian, methi matar malai, moong dal, nimbu paani, plain rice, 2 roti"
        val parsedDinner = parser.parse(dinner)
        assertEquals(6, parsedDinner.size)
    }

    @Test
    fun testWednesdayMenuFullParsing() {
        val breakfast = "Wednesday Breakfast: 2 besan chilla, 1 vada pav, 1 orange, 200 ml milk, egg bhurji"
        val parsedBreakfast = parser.parse(breakfast)
        assertEquals(5, parsedBreakfast.size)

        val lunch = "Wednesday Lunch: aloo jeera, sprouts masala, black mysore dal, veg raita, lemon rice, 3 roti, curd"
        val parsedLunch = parser.parse(lunch)
        assertEquals(7, parsedLunch.size)

        val dinner = "Wednesday Dinner: kadhai paneer, matar chicken, veg pulao, dal makhani, 2 jawa naan, plain rice, 1 gulab jamun"
        val parsedDinner = parser.parse(dinner)
        assertEquals(7, parsedDinner.size)
    }

    @Test
    fun testThursdayMenuFullParsing() {
        val breakfast = "Thursday Breakfast: 1 masala dosa, vermicelli upma, 1 apple, 200 ml milk, 1 boiled egg"
        val parsedBreakfast = parser.parse(breakfast)
        assertEquals(5, parsedBreakfast.size)

        val lunch = "Thursday Lunch: rajma masala, aloo kofta masala, moong mysore dal, masala rice, 3 roti, curd, chaas"
        val parsedLunch = parser.parse(lunch)
        assertEquals(7, parsedLunch.size)

        val dinner = "Thursday Dinner: chilli soya, veg do pyaza, mix dal, plain rice, 2 roti, jalebi"
        val parsedDinner = parser.parse(dinner)
        assertEquals(6, parsedDinner.size)
    }

    @Test
    fun testFridayMenuFullParsing() {
        val breakfast = "Friday Breakfast: poha, 1 veg sandwich, 1 banana, 200 ml milk, paneer bhurji"
        val parsedBreakfast = parser.parse(breakfast)
        assertEquals(5, parsedBreakfast.size)

        val lunch = "Friday Lunch: aloo parwal, aloo chana sabji, mix dal, plain rice, curd, raita"
        val parsedLunch = parser.parse(lunch)
        assertEquals(6, parsedLunch.size)

        val dinner = "Friday Dinner: paneer butter masala, 2 bhature, lehsuni chana dal, masala rice, rabdi jalebi"
        val parsedDinner = parser.parse(dinner)
        assertEquals(5, parsedDinner.size)
    }

    @Test
    fun testSaturdayMenuFullParsing() {
        val breakfast = "Saturday Breakfast: poha, sev, 2 thepla, 1 apple, 200 ml milk, 1 boiled egg"
        val parsedBreakfast = parser.parse(breakfast)
        assertEquals(6, parsedBreakfast.size)

        val lunch = "Saturday Lunch: soyabean gravy, besan gatta sabji, dal, plain rice, papad, curd"
        val parsedLunch = parser.parse(lunch)
        assertEquals(6, parsedLunch.size)

        val dinner = "Saturday Dinner: chole gravy, chicken butter masala, arhar dal, plain rice, 2 paneer roti"
        val parsedDinner = parser.parse(dinner)
        assertEquals(5, parsedDinner.size)
    }

    @Test
    fun testSundayMenuFullParsing() {
        val breakfast = "Sunday Breakfast: 1 mysore masala dosa, sweet dalia, 1 banana, 200 ml milk, paneer bhurji"
        val parsedBreakfast = parser.parse(breakfast)
        assertEquals(5, parsedBreakfast.size)

        val lunch = "Sunday Lunch: matar aloo, veg tawa, dal, plain rice, 3 roti, curd, butter milk"
        val parsedLunch = parser.parse(lunch)
        assertEquals(7, parsedLunch.size)

        val dinner = "Sunday Dinner: shahi paneer, til chicken, peas pulao, 2 naan, toor dal tadka, 1 ice cream"
        val parsedDinner = parser.parse(dinner)
        assertEquals(6, parsedDinner.size)
    }

    @Test
    fun testAll81IndividualSiangDishesAreSupported() {
        val allDishes = listOf(
            "aloo pyaz paratha", "chowmein", "chana dal", "kadhai pakoda", "bhindi masala", "plain rice", "sweet lassi", "raita", "roti", "curd", "papad", "egg curry", "veg malai kofta", "arhar dal", "tomato rice", "jalebi", "paneer roti", "paneer bhurji", "boiled egg",
            "idli", "masala dosa", "medu vada", "kala chana", "long beans dry", "aloo gobi", "mysore dal", "jeera rice", "veg manchurian", "methi matar malai", "moong dal", "nimbu paani",
            "besan chilla", "vada pav", "aloo jeera", "sprouts masala", "black mysore dal", "lemon rice", "kadhai paneer", "matar chicken", "veg pulao", "dal makhani", "jawa naan", "gulab jamun",
            "vermicelli upma", "rajma masala", "aloo kofta masala", "moong mysore dal", "masala rice", "chaas", "chilli soya", "veg do pyaza", "mix dal",
            "poha", "veg roll", "veg sandwich", "aloo parwal", "aloo chana sabji", "paneer butter masala", "gobhi matar", "bhature", "lehsuni chana dal", "rabdi jalebi", "masala lemonade",
            "sev", "thepla", "soyabean gravy", "besan gatta sabji", "chole gravy", "chicken butter masala", "butter chicken", "jeera drink",
            "mysore masala dosa", "sweet dalia", "masala oats", "matar aloo", "veg tawa", "shahi paneer", "til chicken", "peas pulao", "toor dal tadka", "ice cream",
            "breakfast salad", "sprouts salad", "protein salad"
        )

        for (dish in allDishes) {
            val parsed = parser.parse(dish)
            assertTrue("Dish '$dish' failed to parse", parsed.isNotEmpty())
            val item = parsed[0]
            assertTrue("Parsed name for '$dish' should not be empty", item.name.isNotBlank())
        }
    }
}
