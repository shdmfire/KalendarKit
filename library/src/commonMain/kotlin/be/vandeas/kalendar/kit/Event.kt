@file:OptIn(kotlin.time.ExperimentalTime::class)

package be.vandeas.kalendar.kit

import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

data class Event(
    val title: String,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val notes: String? = null,
    val location: String? = null,
    val url: String? = null,
) {
    init {
        validateInternal()
    }

    @Deprecated("Validation now runs during construction.")
    fun validate() {
        validateInternal()
    }

    private fun validateInternal() {
        if (title.isBlank()) {
            throw CalendarValidationException("Event title cannot be blank")
        }

        if (endDate <= startDate) {
            throw CalendarValidationException("Event end date must be after start date")
        }

        validateOptionalUrl(url)
    }
}

data class CalendarEventDraft(
    val title: String,
    val start: Instant,
    val end: Instant,
    val notes: String? = null,
    val location: String? = null,
    val url: String? = null,
    val calendarId: String? = null,
    val alarmMinutesBefore: List<Int> = emptyList()
) {
    init {
        validateInternal()
    }

    @Deprecated("Validation now runs during construction.")
    fun validate() {
        validateInternal()
    }

    private fun validateInternal() {
        if (title.isBlank()) {
            throw CalendarValidationException("Event title cannot be blank")
        }

        if (end <= start) {
            throw CalendarValidationException("Event end date must be after start date")
        }

        if (calendarId != null && calendarId.isBlank()) {
            throw CalendarValidationException("Calendar ID cannot be blank")
        }

        if (alarmMinutesBefore.any { it < 0 }) {
            throw CalendarValidationException("Alarm minutes must be non-negative")
        }

        if (alarmMinutesBefore.distinct().size != alarmMinutesBefore.size) {
            throw CalendarValidationException("Alarm minutes cannot contain duplicate values")
        }

        validateOptionalUrl(url)
    }
}

data class SystemCalendarEvent(
    val id: String,
    val calendarId: String?,
    val title: String,
    val start: Instant,
    val end: Instant,
    val notes: String? = null,
    val location: String? = null,
    val url: String? = null
) {
    init {
        validateInternal()
    }

    @Deprecated("Validation now runs during construction.")
    fun validate() {
        validateInternal()
    }

    private fun validateInternal() {
        if (id.isBlank()) {
            throw CalendarValidationException("Event ID cannot be blank")
        }

        if (calendarId != null && calendarId.isBlank()) {
            throw CalendarValidationException("Calendar ID cannot be blank")
        }

        if (title.isBlank()) {
            throw CalendarValidationException("Event title cannot be blank")
        }

        if (end <= start) {
            throw CalendarValidationException("Event end date must be after start date")
        }

        validateOptionalUrl(url)
    }
}

data class CalendarQuery(
    val from: Instant,
    val to: Instant,
    val calendarIds: List<String> = emptyList()
) {
    init {
        validateInternal()
    }

    @Deprecated("Validation now runs during construction.")
    fun validate() {
        validateInternal()
    }

    private fun validateInternal() {
        if (to <= from) {
            throw CalendarValidationException("Query 'to' date must be after 'from' date")
        }

        if (calendarIds.any { it.isBlank() }) {
            throw CalendarValidationException("Calendar IDs cannot contain blank values")
        }

        if (calendarIds.distinct().size != calendarIds.size) {
            throw CalendarValidationException("Calendar IDs cannot contain duplicate values")
        }
    }
}

sealed interface CalendarPermissionStatus {
    data object NotDetermined : CalendarPermissionStatus
    data object Denied : CalendarPermissionStatus
    data object Restricted : CalendarPermissionStatus
    data object Unknown : CalendarPermissionStatus
    data class Granted(val access: CalendarAccess) : CalendarPermissionStatus
}

private fun validateOptionalUrl(url: String?) {
    if (url == null) return

    val value = url.trim()

    if (value.isEmpty()) {
        throw CalendarValidationException("URL cannot be blank")
    }

    val hasSupportedScheme =
        value.startsWith("http://") ||
        value.startsWith("https://")

    if (!hasSupportedScheme) {
        throw CalendarValidationException("URL must start with http:// or https://")
    }
}
