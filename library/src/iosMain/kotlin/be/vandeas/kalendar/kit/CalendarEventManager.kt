@file:OptIn(ExperimentalTime::class)

package be.vandeas.kalendar.kit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toNSDate
import kotlinx.datetime.toKotlinInstant
import platform.EventKit.*
import platform.EventKitUI.*
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
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
    actual suspend fun insertEvent(event: CalendarEventDraft): String {
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

        return saveEvent(ekEvent, CalendarOperation.Insert)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun queryEvents(query: CalendarQuery): List<SystemCalendarEvent> {
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

        return eventStore.eventsMatchingPredicate(predicate)
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
    actual suspend fun updateEvent(event: SystemCalendarEvent) {
        event.validate()
        ensureReadPermission()

        val ekEvent = eventStore.eventWithIdentifier(event.id)
            ?: throw CalendarEventNotFoundException(event.id)

        ekEvent.title = event.title
        ekEvent.startDate = event.start.toNSDate()
        ekEvent.endDate = event.end.toNSDate()
        ekEvent.location = event.location
        ekEvent.notes = event.notes
        ekEvent.URL = event.url?.let { NSURL(string = it) }

        saveEvent(ekEvent, CalendarOperation.Update)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun deleteEvent(eventId: String) {
        ensureReadPermission()

        val ekEvent = eventStore.eventWithIdentifier(eventId)
            ?: throw CalendarEventNotFoundException(eventId)

        val ok = eventStore.removeEvent(ekEvent, EKSpan.EKSpanThisEvent, true, null)
        if (!ok) {
            throw CalendarOperationFailedException(CalendarOperation.Delete, "Failed to remove event")
        }
    }

    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun openEvent(eventId: String): Boolean {
        val urlString = "calshow:${eventId}"
        val url = NSURL(string = urlString)
        return platform.UIKit.UIApplication.sharedApplication.openURL(url)
    }

    actual suspend fun requestWritePermission(): CalendarPermissionStatus {
        return requestAccess()
    }

    actual suspend fun requestReadWritePermission(): CalendarPermissionStatus {
        return requestAccess()
    }

    private suspend fun requestAccess(): CalendarPermissionStatus = suspendCancellableCoroutine { cont ->
        eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, _ ->
            cont.resume(if (granted) CalendarPermissionStatus.Granted else CalendarPermissionStatus.Denied)
        }
    }

    actual suspend fun currentPermission(): CalendarPermissionStatus {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        return when (status) {
            EKAuthorizationStatusAuthorized -> CalendarPermissionStatus.Granted
            EKAuthorizationStatusDenied -> CalendarPermissionStatus.Denied
            EKAuthorizationStatusRestricted -> CalendarPermissionStatus.Restricted
            EKAuthorizationStatusNotDetermined -> CalendarPermissionStatus.NotDetermined
            else -> CalendarPermissionStatus.Unknown
        }
    }

    private fun ensureWritePermission() {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        if (status == EKAuthorizationStatusNotDetermined || status == EKAuthorizationStatusDenied) {
            val bundle = platform.Foundation.NSBundle.mainBundle
            val hasWriteOnly = bundle.objectForInfoDictionaryKey("NSCalendarsWriteOnlyAccessUsageDescription") != null
            val hasFull = bundle.objectForInfoDictionaryKey("NSCalendarsFullAccessUsageDescription") != null
            val hasLegacy = bundle.objectForInfoDictionaryKey("NSCalendarsUsageDescription") != null

            if (!hasWriteOnly && !hasFull && !hasLegacy) {
                throw CalendarPermissionNotDeclaredException(
                    platform = CalendarPlatform.IOS,
                    message = "Neither NSCalendarsWriteOnlyAccessUsageDescription nor NSCalendarsFullAccessUsageDescription nor NSCalendarsUsageDescription is declared in Info.plist"
                )
            }
        }

        if (status != EKAuthorizationStatusAuthorized) {
            throw CalendarPermissionException(CalendarAccess.WriteOnly)
        }
    }

    private fun ensureReadPermission() {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        if (status == EKAuthorizationStatusNotDetermined || status == EKAuthorizationStatusDenied) {
            val bundle = platform.Foundation.NSBundle.mainBundle
            val hasFull = bundle.objectForInfoDictionaryKey("NSCalendarsFullAccessUsageDescription") != null
            val hasLegacy = bundle.objectForInfoDictionaryKey("NSCalendarsUsageDescription") != null

            if (!hasFull && !hasLegacy) {
                throw CalendarPermissionNotDeclaredException(
                    platform = CalendarPlatform.IOS,
                    message = "Neither NSCalendarsFullAccessUsageDescription nor NSCalendarsUsageDescription is declared in Info.plist"
                )
            }
        }

        if (status != EKAuthorizationStatusAuthorized) {
            throw CalendarPermissionException(CalendarAccess.ReadWrite)
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
