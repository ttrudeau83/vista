package dev.drewett.vista.data.calendar

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FamilyCalendarParserTest {
    private val from = LocalDate.of(2026,11,1)
    private val until = LocalDate.of(2026,12,1)
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test fun recurringRuleAndExceptionDatesAreApplied() {
        val events = parse("""
            BEGIN:VEVENT
            UID:weekly
            DTSTART:20261102T180000Z
            DTEND:20261102T190000Z
            SUMMARY:Family dinner
            RRULE:FREQ=WEEKLY;COUNT=4
            EXDATE:20261109T180000Z
            END:VEVENT
        """)
        assertEquals(3,events.size)
        assertEquals(listOf(2,16,23),events.map { java.time.Instant.ofEpochMilli(it.startMillis).atZone(zone).dayOfMonth })
    }

    @Test fun cancelledAndMovedOccurrencesReplaceTheirMasterInstances() {
        val events = parse("""
            BEGIN:VEVENT
            UID:daily
            DTSTART:20261102T180000Z
            DTEND:20261102T190000Z
            SUMMARY:Routine
            RRULE:FREQ=DAILY;COUNT=3
            END:VEVENT
            BEGIN:VEVENT
            UID:daily
            RECURRENCE-ID:20261103T180000Z
            DTSTART:20261103T180000Z
            STATUS:CANCELLED
            END:VEVENT
            BEGIN:VEVENT
            UID:daily
            RECURRENCE-ID:20261104T180000Z
            DTSTART:20261104T200000Z
            DTEND:20261104T210000Z
            SUMMARY:Moved
            END:VEVENT
        """)
        assertEquals(2,events.size)
        assertEquals("Moved",events.last().title)
        assertEquals(12,java.time.Instant.ofEpochMilli(events.last().startMillis).atZone(zone).hour)
    }

    @Test fun allDayEventsUseCalendarDatesAndExclusiveEnd() {
        val events = parse("""
            BEGIN:VEVENT
            UID:holiday
            DTSTART;VALUE=DATE:20261110
            DTEND;VALUE=DATE:20261112
            SUMMARY:Family trip
            END:VEVENT
        """)
        val event = events.single()
        assertTrue(event.allDay)
        assertTrue(event.occursOn(LocalDate.of(2026,11,10),zone))
        assertTrue(event.occursOn(LocalDate.of(2026,11,11),zone))
        assertFalse(event.occursOn(LocalDate.of(2026,11,9),zone))
        assertFalse(event.occursOn(LocalDate.of(2026,11,12),zone))
    }

    @Test fun timedEventAcrossMidnightAppearsOnBothDays() {
        val event = parse("""
            BEGIN:VEVENT
            UID:late
            DTSTART:20261111T073000Z
            DTEND:20261111T093000Z
            SUMMARY:Late event
            END:VEVENT
        """).single()
        assertTrue(event.occursOn(LocalDate.of(2026,11,10),zone))
        assertTrue(event.occursOn(LocalDate.of(2026,11,11),zone))
        assertFalse(event.occursOn(LocalDate.of(2026,11,12),zone))
    }

    @Test fun malformedResponseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { FamilyCalendarParser.parse("<html>Sign in</html>",from,until,zone) }
    }

    @Test fun cancelledMasterDoesNotGenerateEvents() {
        assertTrue(parse("""
            BEGIN:VEVENT
            UID:cancelled
            DTSTART:20261102T180000Z
            STATUS:CANCELLED
            RRULE:FREQ=DAILY;COUNT=10
            END:VEVENT
        """).isEmpty())
    }

    private fun parse(events: String): List<FamilyEvent> = FamilyCalendarParser.parse(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Vista tests//EN\r\n" + events.trimIndent().replace("\n","\r\n") + "\r\nEND:VCALENDAR\r\n",from,until,zone)
}
