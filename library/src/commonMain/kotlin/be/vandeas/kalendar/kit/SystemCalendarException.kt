package be.vandeas.kalendar.kit

sealed class SystemCalendarException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class CalendarPermissionException(
    val requiredAccess: CalendarAccess,
    message: String = "Calendar permission is not granted: $requiredAccess",
    cause: Throwable? = null
) : SystemCalendarException(message, cause)

class CalendarPermissionNotDeclaredException(
    val platform: CalendarPlatform,
    message: String
) : SystemCalendarException(message)

class CalendarUnavailableException(
    message: String = "System calendar is unavailable",
    cause: Throwable? = null
) : SystemCalendarException(message, cause)

class CalendarEventNotFoundException(
    val eventId: String,
    message: String = "Calendar event not found: $eventId"
) : SystemCalendarException(message)

class CalendarValidationException(
    message: String,
    cause: Throwable? = null
) : SystemCalendarException(message, cause)

class CalendarOperationFailedException(
    val operation: CalendarOperation,
    message: String,
    cause: Throwable? = null
) : SystemCalendarException(message, cause)

enum class CalendarAccess {
    WriteOnly,
    ReadWrite
}

enum class CalendarPlatform {
    Android,
    IOS
}

enum class CalendarOperation {
    OpenAddEvent,
    Insert,
    Query,
    Update,
    Delete
}
