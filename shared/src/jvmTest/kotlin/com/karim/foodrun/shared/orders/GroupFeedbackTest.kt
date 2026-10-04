package com.karim.foodrun.shared.orders

import kotlin.test.*

class GroupFeedbackTest {
    @Test fun successAndFailureExpireAndOldDismissalsCannotHideNewMessages() {
        var now = 100L
        val feedback = GroupFeedbacks { now }
        feedback.show("Saved", false)
        val saved = assertNotNull(feedback.current)
        now += 3_999
        assertNotNull(feedback.current)
        now++
        assertNull(feedback.current)
        feedback.show("Connection failed", true)
        val failed = assertNotNull(feedback.current)
        assertTrue(failed.isError)
        feedback.dismiss(saved.id)
        assertEquals(failed, feedback.current)
        now += 7_000
        assertNull(feedback.current)
        feedback.show("Connection failed", true)
        val repeated = assertNotNull(feedback.current)
        assertNotEquals(failed.id, repeated.id)
        feedback.dismiss(failed.id)
        assertEquals(repeated, feedback.current)
        feedback.dismiss(repeated.id)
        assertNull(feedback.current)
    }

    @Test fun clearingAnErrorDoesNotHideASuccessMessage() {
        val feedback = GroupFeedbacks { 0L }
        feedback.show("Saved", false)
        feedback.clearError()
        assertNotNull(feedback.current)
        feedback.show("Failed", true)
        feedback.clearError()
        assertNull(feedback.current)
    }
}
