package be.vandeas.kalendar.kit

import kotlinx.coroutines.CancellationException

expect class CalendarEventManager() {

    /**
     * Creates and saves a new calendar event by opening the system UI.
     * Returns true if the event was successfully created.
     */
    @Deprecated("Use openAddEvent instead", ReplaceWith("openAddEvent(value)"))
    suspend fun createEvent(
        value: Event
    ): Boolean

    /**
     * Opens the system calendar event editor with pre-populated event data,
     * allowing the user to review and save the event.
     */
    @Throws(SystemCalendarException::class, CancellationException::class)
    suspend fun openAddEvent(event: Event): Boolean

    /**
     * Programmatically inserts an event into the system calendar.
     * Requires WRITE_CALENDAR permission.
     * @return The ID of the inserted event.
     */
    @Throws(SystemCalendarException::class, CancellationException::class)
    suspend fun insertEvent(event: CalendarEventDraft): String

    /**
     * Programmatically queries events from the system calendar.
     * Requires READ_CALENDAR permission.
     */
    @Throws(SystemCalendarException::class, CancellationException::class)
    suspend fun queryEvents(query: CalendarQuery): List<SystemCalendarEvent>

    /**
     * Programmatically updates an existing event in the system calendar.
     * Requires WRITE_CALENDAR permission.
     */
    @Throws(SystemCalendarException::class, CancellationException::class)
    suspend fun updateEvent(event: SystemCalendarEvent)

    /**
     * Programmatically deletes an event from the system calendar.
     * Requires WRITE_CALENDAR permission.
     */
    @Throws(SystemCalendarException::class, CancellationException::class)
    suspend fun deleteEvent(eventId: String)

    /**
     * Opens the system calendar app to display a specific event.
     */
    @Throws(SystemCalendarException::class, CancellationException::class)
    suspend fun openEvent(eventId: String): Boolean

    /**
     * Requests write-only permission for the calendar.
     */
    suspend fun requestWritePermission(): CalendarPermissionStatus

    /**
     * Requests full read/write permission for the calendar.
     */
    suspend fun requestReadWritePermission(): CalendarPermissionStatus

    /**
     * Returns the current calendar permission status.
     */
    suspend fun currentPermission(): CalendarPermissionStatus
}
