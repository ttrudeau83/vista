package dev.drewett.vista.data.calendar

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.util.ICalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.TimeZone

data class FamilyEvent(val id: String, val title: String, val startMillis: Long, val endMillis: Long, val allDay: Boolean) {
    fun occursOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return startMillis < until && endMillis > from
    }
}

/** Uses biweekly for RFC5545 parsing, VTIMEZONE, recurrence rules and EXDATE. */
object FamilyCalendarParser {
    fun parse(ics: String, from: LocalDate = LocalDate.now().minusMonths(1).withDayOfMonth(1),
              until: LocalDate = LocalDate.now().plusMonths(7).withDayOfMonth(1),
              zone: ZoneId = ZoneId.systemDefault()): List<FamilyEvent> {
        require(ics.contains("BEGIN:VCALENDAR") && ics.length <= 2 * 1024 * 1024) { "This is not a supported iCal calendar." }
        val calendars = Biweekly.parse(ics).all()
        require(calendars.isNotEmpty()) { "The calendar could not be read." }
        val output = mutableListOf<FamilyEvent>()
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val untilMillis = until.atStartOfDay(zone).toInstant().toEpochMilli()
        calendars.forEach { calendar ->
            require(calendar.events.size <= 10_000) { "This calendar contains too many entries." }
            val grouped = calendar.events.groupBy { it.uid?.value ?: "missing-${System.identityHashCode(it)}" }
            grouped.forEach { (uid, events) ->
                val overrides = events.filter { it.recurrenceId != null }
                require(overrides.none { it.recurrenceId.range != null }) { "This calendar uses an unsupported future-occurrence override." }
                val overrideDates = overrides.mapNotNull { it.recurrenceId?.value?.let { d -> dateMillis(d, zone) } }.toSet()
                val master = events.filter { it.recurrenceId == null }.maxByOrNull { it.sequence?.value ?: 0 }
                if (master != null && !cancelled(master)) {
                    val start = master.dateStart?.value
                    if (start != null) {
                        val allDay = !start.hasTime()
                        val tz = calendar.timezoneInfo.let { info ->
                            if (info.isFloating(master.dateStart)) TimeZone.getTimeZone(zone)
                            else info.getTimezone(master.dateStart)?.timeZone ?: TimeZone.getTimeZone("UTC")
                        }
                        val iterator = master.getDateIterator(tz)
                        // Include events spanning the boundary; duration itself is bounded below.
                        iterator.advanceTo(Date(fromMillis - 366L * 86_400_000))
                        var count = 0
                        while (iterator.hasNext()) {
                            require(++count <= 20_000) { "This calendar recurrence is too frequent." }
                            val occurrence = iterator.next()
                            val occurrenceMillis = if (allDay) occurrence.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() else occurrence.time
                            if (occurrenceMillis >= untilMillis) break
                            if (occurrenceMillis !in overrideDates) {
                                event(master, uid, occurrenceMillis, zone)?.takeIf { it.endMillis > fromMillis }?.let(output::add)
                            }
                            require(output.size <= 5_000) { "This calendar has too many upcoming occurrences." }
                        }
                    }
                }
                overrides.groupBy { it.recurrenceId.value.time }.values.forEach { versions ->
                    val override = versions.maxByOrNull { it.sequence?.value ?: 0 }!!
                    if (!cancelled(override)) override.dateStart?.value?.let { start ->
                        event(override, uid, dateMillis(start, zone), zone)?.takeIf { it.endMillis > fromMillis && it.startMillis < untilMillis }?.let(output::add)
                    }
                }
            }
        }
        return output.distinctBy { it.id }.sortedBy { it.startMillis }
    }

    private fun event(event: VEvent, uid: String, occurrence: Long, zone: ZoneId): FamilyEvent? {
        val start = event.dateStart?.value ?: return null
        val allDay = !start.hasTime()
        val base = dateMillis(start, zone)
        val end = event.dateEnd?.value?.let { dateMillis(it, zone) }
        val duration = end?.minus(base) ?: event.duration?.value?.toMillis() ?: if (allDay) 86_400_000 else 1
        require(duration in 0..(366L * 86_400_000)) { "An event has an unsupported duration." }
        val occurrenceEnd = if (allDay) {
            val days = event.dateEnd?.value?.let { ChronoUnit.DAYS.between(localDate(start), localDate(it)) } ?: (duration / 86_400_000).coerceAtLeast(1)
            Instant.ofEpochMilli(occurrence).atZone(zone).toLocalDate().plusDays(days.coerceAtLeast(1)).atStartOfDay(zone).toInstant().toEpochMilli()
        } else occurrence + duration.coerceAtLeast(1)
        return FamilyEvent("$uid/$occurrence", event.summary?.value?.take(300)?.ifBlank { "Untitled event" } ?: "Untitled event", occurrence, occurrenceEnd, allDay)
    }

    private fun cancelled(event: VEvent) = event.status?.value.equals("CANCELLED", ignoreCase = true)
    private fun localDate(date: ICalDate): LocalDate = date.rawComponents?.let { LocalDate.of(it.year, it.month, it.date) }
        ?: date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    private fun dateMillis(date: ICalDate, zone: ZoneId): Long = if (!date.hasTime()) localDate(date).atStartOfDay(zone).toInstant().toEpochMilli() else date.time
}
