package com.health.friday.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodParserTest {

    private val parser = FoodParser()

    @Test
    fun parseScrambledEggsAndToast() {
        val result = parser.parse("I had two scrambled eggs and a piece of toast.")

        assertEquals(2, result.size)

        val egg = result[0]
        assertEquals("scrambled egg", egg.name)
        assertEquals(2.0, egg.quantity, 0.01)
        assertEquals("piece", egg.unit)

        val toast = result[1]
        assertEquals("toast", toast.name)
        assertEquals(1.0, toast.quantity, 0.01)
        assertEquals("piece", toast.unit)
    }

    @Test
    fun parseTwoEggsAndToast() {
        val result = parser.parse("I ate 2 eggs and toast")

        assertEquals(2, result.size)

        val egg = result[0]
        assertEquals("egg", egg.name)
        assertEquals(2.0, egg.quantity, 0.01)
        assertEquals("piece", egg.unit)

        val toast = result[1]
        assertEquals("toast", toast.name)
        assertEquals(1.0, toast.quantity, 0.01)
        assertEquals("piece", toast.unit)
    }

    @Test
    fun parseOneCupOfRice() {
        val result = parser.parse("1 cup of rice")

        assertEquals(1, result.size)
        val rice = result[0]
        assertEquals("rice", rice.name)
        assertEquals(1.0, rice.quantity, 0.01)
        assertEquals("cup", rice.unit)
    }

    @Test
    fun parseTwoSlicesOfBread() {
        val result = parser.parse("2 slices of bread")

        assertEquals(1, result.size)
        val bread = result[0]
        assertEquals("bread", bread.name)
        assertEquals(2.0, bread.quantity, 0.01)
        assertEquals("slice", bread.unit)
    }

    @Test
    fun parseHalfCupOfMilk() {
        val result = parser.parse("half a cup of milk")

        assertEquals(1, result.size)
        val milk = result[0]
        assertEquals("milk", milk.name)
        assertEquals(0.5, milk.quantity, 0.01)
        assertEquals("cup", milk.unit)
    }

    @Test
    fun parseBoiledEggsAndApple() {
        val result = parser.parse("3 boiled eggs and an apple")

        assertEquals(2, result.size)

        val egg = result[0]
        assertEquals("boiled egg", egg.name)
        assertEquals(3.0, egg.quantity, 0.01)

        val apple = result[1]
        assertEquals("apple", apple.name)
        assertEquals(1.0, apple.quantity, 0.01)
    }

    @Test
    fun parseIndianFullMealWithChapatis() {
        val result = parser.parse("I had 2 chapatis, a bowl of sabji, a bowl of dal, a plate of rice, and salad")

        assertEquals(5, result.size)

        assertEquals("chapati", result[0].name)
        assertEquals(2.0, result[0].quantity, 0.01)
        assertEquals("piece", result[0].unit)

        assertEquals("sabji", result[1].name)
        assertEquals(1.0, result[1].quantity, 0.01)
        assertEquals("bowl", result[1].unit)

        assertEquals("dal", result[2].name)
        assertEquals(1.0, result[2].quantity, 0.01)
        assertEquals("bowl", result[2].unit)

        assertEquals("rice", result[3].name)
        assertEquals(1.0, result[3].quantity, 0.01)
        assertEquals("plate", result[3].unit)

        assertEquals("salad", result[4].name)
        assertEquals(1.0, result[4].quantity, 0.01)
        assertEquals("piece", result[4].unit)
    }

    @Test
    fun parseIndianMealWithRotisAndSubzi() {
        val result = parser.parse("I ate two rotis, dal, subzi, rice and salad")

        assertEquals(5, result.size)

        assertEquals("roti", result[0].name)
        assertEquals(2.0, result[0].quantity, 0.01)

        assertEquals("dal", result[1].name)
        assertEquals(1.0, result[1].quantity, 0.01)

        assertEquals("subzi", result[2].name)
        assertEquals(1.0, result[2].quantity, 0.01)

        assertEquals("rice", result[3].name)
        assertEquals(1.0, result[3].quantity, 0.01)

        assertEquals("salad", result[4].name)
        assertEquals(1.0, result[4].quantity, 0.01)
    }

    @Test
    fun parseIndianBreakfastIdliDosa() {
        val result = parser.parse("2 idlis and a dosa")

        assertEquals(2, result.size)

        assertEquals("idli", result[0].name)
        assertEquals(2.0, result[0].quantity, 0.01)

        assertEquals("dosa", result[1].name)
        assertEquals(1.0, result[1].quantity, 0.01)
    }

    @Test
    fun parseHostelMessThaliWithPaneerSabjiAndGulabJamun() {
        val text = "3 chapati and paneer sabji like regular mess dish rice and dal salad (cucumer 3-4 peices when you cut it circular simialr beet root carrots and tamatoes) then one piece of gulab jamun"
        val result = parser.parse(text)

        assertEquals(6, result.size)

        assertEquals("chapati", result[0].name)
        assertEquals(3.0, result[0].quantity, 0.01)

        assertEquals("paneer sabji", result[1].name)
        assertEquals(1.0, result[1].quantity, 0.01)

        assertEquals("rice", result[2].name)
        assertEquals(1.0, result[2].quantity, 0.01)

        assertEquals("dal", result[3].name)
        assertEquals(1.0, result[3].quantity, 0.01)

        assertEquals("salad", result[4].name)
        assertEquals(1.0, result[4].quantity, 0.01)

        assertEquals("gulab jamun", result[5].name)
        assertEquals(1.0, result[5].quantity, 0.01)
    }

    @Test
    fun parseCoffeeBreakfastSaladAndBesanChilla() {
        val text = "I ate cup of coffee, breakfast salad, 3 besan chilla"
        val result = parser.parse(text)

        assertEquals(3, result.size)

        assertEquals("coffee", result[0].name)
        assertEquals(1.0, result[0].quantity, 0.01)
        assertEquals("cup", result[0].unit)

        assertEquals("breakfast salad", result[1].name)
        assertEquals(1.0, result[1].quantity, 0.01)

        assertEquals("besan chilla", result[2].name)
        assertEquals(3.0, result[2].quantity, 0.01)
    }

    @Test
    fun parseSiangHostelWeeklyMessMenu() {
        val mondayLunch = "Monday Lunch: Chana Dal, Kadhai Pakoda, Bhindi Masala, Plain Rice, Sweet Lassi, 3 Roti, Curd, Papad"
        val mondayResult = parser.parse(mondayLunch)
        assertEquals(8, mondayResult.size)

        val wednesdayDinner = "Wednesday Dinner: Kadhai Paneer, Matar Chicken, Veg Pulao, Dal Makhani, 2 Jawa Naan, Plain Rice, 1 Gulab Jamun"
        val wednesdayResult = parser.parse(wednesdayDinner)
        assertEquals(7, wednesdayResult.size)

        val sundayDinner = "Sunday Dinner: Shahi Paneer, Til Chicken, Peas Pulao, 2 Naan, Toor Dal Tadka, 1 Ice Cream"
        val sundayResult = parser.parse(sundayDinner)
        assertEquals(6, sundayResult.size)
    }
}
