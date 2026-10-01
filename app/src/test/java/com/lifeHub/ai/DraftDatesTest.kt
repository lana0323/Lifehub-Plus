package com.lifeHub.ai

import com.lifeHub.ai.data.DraftDates
import org.junit.Assert.*
import org.junit.Test

class DraftDatesTest {
    @Test fun validatesActualCalendarDates() {
        assertTrue(DraftDates.valid("2028-02-29"))
        listOf("2026-02-29", "2026-04-31", "2026-13-01", "2026-1-02", "2026-01-01junk").forEach {
            assertFalse(it, DraftDates.valid(it))
        }
    }
}
