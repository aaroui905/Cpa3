package com.example

import com.example.data.model.GeneratedIdentity
import com.example.data.model.OfferClickItem
import com.example.service.AutomationScriptBuilder
import com.example.service.TaskCategoryPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfferClickTest {

    @Test
    fun testOfferClickIsPriorityOne() {
        // Test that "offer_click" is always assigned priority = 1
        val def = TaskCategoryPlanner.findDefinition("Offer Click")
        assertNotNull(def)
        assertEquals(1, def?.priority)
        assertEquals("offer_click", def?.id)
    }

    @Test
    fun testPlannerSortingWithOfferClick() {
        // Ensure that Offer Click is always sorted first before any other categories
        val rawCategories = listOf(
            "Survey / Quiz",
            "Confirmation",
            "Offer Click",
            "Email Submit",
            "Skip Upsells"
        )
        val orderedSteps = TaskCategoryPlanner.orderCategories(rawCategories)
        assertEquals("offer_click", orderedSteps.first().id)
        assertEquals(1, orderedSteps.first().priority)
    }

    @Test
    fun testOfferClickSequenceSelection() {
        // Test sequential round-robin execution through enabled items only
        val items = listOf(
            OfferClickItem(id = 1L, text = "Get \$1000 Walmart gift card", enabled = true, orderIndex = 0),
            OfferClickItem(id = 2L, text = "Claim \$750 Cash App Reward", enabled = false, orderIndex = 1),
            OfferClickItem(id = 3L, text = "Get \$500 Amazon Gift Card", enabled = true, orderIndex = 2)
        )

        val activeList = items.filter { it.enabled }.sortedBy { it.orderIndex }
        assertEquals(2, activeList.size)
        assertEquals("Get \$1000 Walmart gift card", activeList[0].text)
        assertEquals("Get \$500 Amazon Gift Card", activeList[1].text)

        // Loop index 0:
        val pick0 = activeList[0 % activeList.size]
        assertEquals("Get \$1000 Walmart gift card", pick0.text)

        // Loop index 1:
        val pick1 = activeList[1 % activeList.size]
        assertEquals("Get \$500 Amazon Gift Card", pick1.text)

        // Loop index 2 (wraps around):
        val pick2 = activeList[2 % activeList.size]
        assertEquals("Get \$1000 Walmart gift card", pick2.text)
    }

    @Test
    fun testScriptBuilderContainsOfferClickLogic() {
        val identity = GeneratedIdentity(
            firstName = "John",
            lastName = "Smith",
            fullName = "John Smith",
            email = "john.smith@gmail.com",
            phone = "2125550199",
            address = "123 Main St",
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "United States",
            birthDate = "1990-05-12",
            gender = "Male"
        )

        val script = AutomationScriptBuilder.buildSmartFormFillScript(
            identity = identity,
            categories = "Offer Click, Survey / Quiz, Email Submit",
            clickTexts = listOf("Get \$1000 Walmart gift card", "Claim \$750 Cash App Reward"),
            activeClickText = "Get \$1000 Walmart gift card"
        )

        // Verify key components exist in generated script
        assertTrue(script.contains("handleOfferClick"))
        assertTrue(script.contains("Get $1000 Walmart gift card"))
        assertTrue(script.contains("checkMatch"))
        assertTrue(script.contains("window.AndroidBridge.onOfferClicked"))
        assertTrue(script.contains("PRIORITY #1 - OFFER CLICK"))
    }
}
