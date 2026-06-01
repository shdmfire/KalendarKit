@file:OptIn(ExperimentalTime::class)

package be.vandeas.kalendar.kit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toNSDate
import kotlinx.datetime.toKotlinInstant
import platform.EventKit.*
import platform.EventKitUI.*
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import platform.UIKit.navigationController
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime

actual class CalendarEventManager {
    private val eventStore = EKEventStore()
    private lateinit var presentingViewController: UIViewController
    private var editDelegate: EKEventEditViewDelegateProtocol? = null

    fun setPresentingViewController(viewController: UIViewController) {
        this.presentingViewController = viewController
    }

    @Deprecated("Use openAddEvent instead", ReplaceWith("openAddEvent(value)"))
    actual suspend fun createEvent(value: Event): Boolean {
        return openAddEvent(value)
    }

    @OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun openAddEvent(event: Event): Boolean {
        event.validate()
        if (!::presentingViewController.isInitialized) {
            throw CalendarOperationFailedException(CalendarOperation.OpenAddEvent, "presentingViewController is not initialized")
        }

        val startTime = event.startDate.toInstant(TimeZone.currentSystemDefault()).toNSDate()
        val endTime = event.endDate.toInstant(TimeZone.currentSystemDefault()).toNSDate()

        val ekEvent = EKEvent.eventWithEventStore(eventStore).apply {
            title = event.title
            startDate = startTime
            endDate = endTime
            location = event.location
            notes = event.notes
            calendar = eventStore.defaultCalendarForNewEvents
            URL = event.url?.let { NSURL(string = it) }
        }

        val delegate = object : NSObject(), EKEventEditViewDelegateProtocol {
            override fun eventEditViewController(
                controller: EKEventEditViewController,
                didCompleteWithAction: Long
            ) {
                controller.dismissViewControllerAnimated(true, null)
            }
        }
        editDelegate = delegate

        val eventEditVC = EKEventEditViewController().apply {
            this.event = ekEvent
            this.eventStore = this@CalendarEventManager.eventStore
            this.editViewDelegate = delegate
        }

        presentingViewController.presentViewController(eventEditVC, true, null)
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun insertEvent(event: CalendarEventDraft): String = withContext(Dispatchers.IO) {
        event.validate()
        ensureWritePermission()

        val ekEvent = EKEvent.eventWithEventStore(eventStore).apply {
            title = event.title
            startDate = event.start.toNSDate()
            endDate = event.end.toNSDate()
            location = event.location
            notes = event.notes
            calendar = event.calendarId?.let { eventStore.calendarWithIdentifier(it) } ?: eventStore.defaultCalendarForNewEvents
            URL = event.url?.let { NSURL(string = it) }
        }

        // Alarms
        event.alarmMinutesBefore.forEach { minutes ->
            addAlarm(ekEvent, minutes.toDouble() * -60.0)
        }

        return@withContext saveEvent(ekEvent, CalendarOperation.Insert)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun queryEvents(query: CalendarQuery): List<SystemCalendarEvent> = withContext(Dispatchers.IO) {
        query.validate()
        ensureReadPermission()

        val calendars = if (query.calendarIds.isEmpty()) {
            null
        } else {
            eventStore.calendarsForEntityType(EKEntityType.EKEntityTypeEvent).filterIsInstance<EKCalendar>()
                .filter { it.calendarIdentifier in query.calendarIds }
        }

        val predicate = eventStore.predicateForEventsWithStartDate(
            startDate = query.from.toNSDate(),
            endDate = query.to.toNSDate(),
            calendars = calendars
        )

        return@withContext eventStore.eventsMatchingPredicate(predicate)
            .filterIsInstance<EKEvent>()
            .mapNotNull { event ->
                val id = event.eventIdentifier ?: return@mapNotNull null
                SystemCalendarEvent(
                    id = id,
                    calendarId = event.calendar?.calendarIdentifier,
                    title = event.title.orEmpty(),
                    start = event.startDate?.toKotlinInstant() ?: return@mapNotNull null,
                    end = event.endDate?.toKotlinInstant() ?: return@mapNotNull null,
                    notes = event.notes,
                    location = event.location,
                    url = event.URL?.absoluteString
                )
            }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun updateEvent(event: SystemCalendarEvent): Unit = withContext(Dispatchers.IO) {
        event.validate()
        ensureWritePermission()

        val ekEvent = eventStore.eventWithIdentifier(event.id)
            ?: throw CalendarEventNotFoundException(event.id)

        ekEvent.title = event.title
        ekEvent.startDate = event.start.toNSDate()
        ekEvent.endDate = event.end.toNSDate()
        ekEvent.location = event.location
        ekEvent.notes = event.notes
        ekEvent.URL = event.url?.let { NSURL(string = it) }

        saveEvent(ekEvent, CalendarOperation.Update)
        Unit
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun deleteEvent(eventId: String) = withContext(Dispatchers.IO) {
        ensureWritePermission()

        val ekEvent = eventStore.eventWithIdentifier(eventId)
            ?: throw CalendarEventNotFoundException(eventId)

        val ok = eventStore.removeEvent(ekEvent, EKSpan.EKSpanThisEvent, true, null)
        if (!ok) {
            throw CalendarOperationFailedException(CalendarOperation.Delete, "Failed to remove event")
        }
    }

    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun openEvent(eventId: String): Boolean = withContext(Dispatchers.Main) {
        if (!::presentingViewController.isInitialized) {
            throw CalendarOperationFailedException(CalendarOperation.OpenAddEvent, "presentingViewController is not initialized")
        }

        val event = withContext(Dispatchers.IO) {
            eventStore.eventWithIdentifier(eventId)
        } ?: throw CalendarEventNotFoundException(eventId)

        val eventViewController = EKEventViewController().apply {
            this.event = event
            allowsEditing = true
            allowsCalendarPreview = true
        }

        presentingViewController.navigationController
            ?.pushViewController(eventViewController, true)
            ?: presentingViewController.presentViewController(eventViewController, true, null)

        true
    }

    actual suspend fun requestWritePermission(): CalendarPermissionStatus {
        return requestAccess(CalendarAccess.WriteOnly)
    }

    actual suspend fun requestReadWritePermission(): CalendarPermissionStatus {
        return requestAccess(CalendarAccess.ReadWrite)
    }

    private suspend fun requestAccess(access: CalendarAccess): CalendarPermissionStatus = suspendCancellableCoroutine { cont ->
        ensurePermissionDescriptionDeclared(access)
        when (access) {
            CalendarAccess.WriteOnly -> {
                eventStore.requestWriteOnlyAccessToEventsWithCompletion { granted, _ ->
                    cont.resume(if (granted) CalendarPermissionStatus.WriteOnly else CalendarPermissionStatus.Denied)
                }
            }
            CalendarAccess.ReadWrite -> {
                eventStore.requestFullAccessToEventsWithCompletion { granted, _ ->
                    cont.resume(if (granted) CalendarPermissionStatus.Granted else CalendarPermissionStatus.Denied)
                }
            }
        }
    }

    actual suspend fun currentPermission(): CalendarPermissionStatus = withContext(Dispatchers.IO) {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        return@withContext when (status) {
            EKAuthorizationStatusAuthorized -> CalendarPermissionStatus.Granted
            EKAuthorizationStatusFullAccess -> CalendarPermissionStatus.Granted
            EKAuthorizationStatusWriteOnly -> CalendarPermissionStatus.WriteOnly
            EKAuthorizationStatusDenied -> CalendarPermissionStatus.Denied
            EKAuthorizationStatusRestricted -> CalendarPermissionStatus.Restricted
            EKAuthorizationStatusNotDetermined -> CalendarPermissionStatus.NotDetermined
            else -> CalendarPermissionStatus.Unknown
        }
    }

    private fun ensureWritePermission() {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        if (status == EKAuthorizationStatusNotDetermined || status == EKAuthorizationStatusDenied) {
            ensurePermissionDescriptionDeclared(CalendarAccess.WriteOnly)
        }

        if (status != EKAuthorizationStatusAuthorized &&
            status != EKAuthorizationStatusFullAccess &&
            status != EKAuthorizationStatusWriteOnly
        ) {
            throw CalendarPermissionException(CalendarAccess.WriteOnly)
        }
    }

    private fun ensureReadPermission() {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        if (status == EKAuthorizationStatusNotDetermined || status == EKAuthorizationStatusDenied) {
            ensurePermissionDescriptionDeclared(CalendarAccess.ReadWrite)
        }

        if (status != EKAuthorizationStatusAuthorized &&
            status != EKAuthorizationStatusFullAccess
        ) {
            throw CalendarPermissionException(CalendarAccess.ReadWrite)
        }
    }

    private fun ensurePermissionDescriptionDeclared(access: CalendarAccess) {
        val bundle = platform.Foundation.NSBundle.mainBundle
        val hasWriteOnly = bundle.objectForInfoDictionaryKey("NSCalendarsWriteOnlyAccessUsageDescription") != null
        val hasFull = bundle.objectForInfoDictionaryKey("NSCalendarsFullAccessUsageDescription") != null
        val hasLegacy = bundle.objectForInfoDictionaryKey("NSCalendarsUsageDescription") != null

        val isDeclared = when (access) {
            CalendarAccess.WriteOnly -> hasWriteOnly || hasFull || hasLegacy
            CalendarAccess.ReadWrite -> hasFull || hasLegacy
        }

        if (!isDeclared) {
            val message = when (access) {
                CalendarAccess.WriteOnly ->
                    "Neither NSCalendarsWriteOnlyAccessUsageDescription nor NSCalendarsFullAccessUsageDescription nor NSCalendarsUsageDescription is declared in Info.plist"
                CalendarAccess.ReadWrite ->
                    "Neither NSCalendarsFullAccessUsageDescription nor NSCalendarsUsageDescription is declared in Info.plist"
            }
            throw CalendarPermissionNotDeclaredException(CalendarPlatform.IOS, message)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun saveEvent(event: EKEvent, operation: CalendarOperation): String {
        val ok = eventStore.saveEvent(event, EKSpan.EKSpanThisEvent, true, null)
        if (!ok) {
            throw CalendarOperationFailedException(operation, "Failed to save event")
        }
        return event.eventIdentifier ?: throw CalendarOperationFailedException(operation, "Saved event has no identifier")
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun addAlarm(event: EKEvent, offsetSeconds: Double) {
        val alarm = EKAlarm.alarmWithRelativeOffset(offsetSeconds)
        event.addAlarm(alarm)
    }
}
