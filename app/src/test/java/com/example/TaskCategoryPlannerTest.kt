package com.example

import com.example.service.TaskCategoryPlanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCategoryPlannerTest {
    @Test
    fun buildAdaptivePagePlan_detectsSurveyAndBuildsWorkMap() {
        val plan = TaskCategoryPlanner.buildAdaptivePagePlan(
            url = "https://example.com/quiz?offer=reward",
            categories = listOf("Email Submit", "Survey / Quiz", "Confirmation"),
            contextText = "Need to answer the survey questions and submit the email to get the reward"
        )

        assertTrue(plan.pageType.isNotBlank())
        assertTrue(plan.workMap.isNotEmpty())
        assertTrue(plan.pageActions.any { it.action.contains("survey") || it.action.contains("field") || it.action.contains("scroll") })
        assertFalse(plan.summary.isBlank())
    }

    @Test
    fun buildAdaptivePagePlan_handlesUnknownPageWithFallbackStrategy() {
        val plan = TaskCategoryPlanner.buildAdaptivePagePlan(
            url = "https://example.com/custom-offer-landing",
            categories = emptyList(),
            contextText = "The page asks for contact info and then a button to continue"
        )

        assertTrue(plan.pageType.isNotBlank())
        assertTrue(plan.pageActions.isNotEmpty())
        assertTrue(plan.workMap.any { it.target.contains("field") || it.target.contains("button") || it.target.contains("scroll") })
    }
}
