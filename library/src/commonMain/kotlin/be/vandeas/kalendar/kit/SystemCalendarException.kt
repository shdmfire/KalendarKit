package be.vandeas.kalendar.kit

sealed class SystemCalendarException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class CalendarPermissionException(
    override val requiredAccess: CalendarAccess,
    override val reason: CalendarPermissionReason = CalendarPermissionReason.NotGranted,
    message: String = "Calendar permission is not granted: $requiredAccess",
    cause: Throwable? = null
) : SystemCalendarException(message, cause), CalendarPermissionProblem

class CalendarPermissionNotDeclaredException(
    val platform: CalendarPlatform,
    message: String,
    override val requiredAccess: CalendarAccess? = null
) : SystemCalendarException(message), CalendarPermissionProblem {
    override val reason: CalendarPermissionReason = CalendarPermissionReason.NotDeclared
}

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

interface CalendarPermissionProblem {
    val reason: CalendarPermissionReason
    val requiredAccess: CalendarAccess?
}

enum class CalendarPermissionReason {
    NotGranted,
    NotDeclared
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
