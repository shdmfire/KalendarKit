package be.vandeas.kalendar.kit

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.util.TimeZone as JTimeZone
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

actual class CalendarEventManager {
    private lateinit var context: Context

    fun setup(context: Context) {
        this.context = context
    }

    @Deprecated("Use openAddEvent instead", ReplaceWith("openAddEvent(value)"))
    actual suspend fun createEvent(value: Event): Boolean {
        return openAddEvent(value)
    }

    @OptIn(ExperimentalTime::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun openAddEvent(event: Event): Boolean {
        event.validate()
        val startTime = event.startDate.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val endTime = event.endDate.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        val notes = buildString {
            event.url?.let { append("URL: $it\n") }
            event.notes?.let { append(it) }
        }

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, notes)
            putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
        }

        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val canHandle = intent.resolveActivity(context.packageManager) != null
        if (!canHandle) {
            throw CalendarUnavailableException("No calendar app can handle ACTION_INSERT")
        }

        context.startActivity(intent)
        return true
    }

    @OptIn(ExperimentalTime::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun insertEvent(event: CalendarEventDraft): String = withContext(Dispatchers.IO) {
        event.validate()
        ensureWritePermission()

        val calendarId = event.calendarId ?: findDefaultCalendarId()
            ?: throw CalendarUnavailableException("No writable calendar found")

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.notes)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.start.toEpochMilliseconds())
            put(CalendarContract.Events.DTEND, event.end.toEpochMilliseconds())
            put(CalendarContract.Events.EVENT_TIMEZONE, JTimeZone.getDefault().id)
        }

        try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: throw CalendarOperationFailedException(
                    operation = CalendarOperation.Insert,
                    message = "ContentResolver.insert returned null"
                )

            val eventId = ContentUris.parseId(uri).toString()

            event.alarmMinutesBefore.forEach { minutes ->
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, parseEventId(eventId))
                    put(CalendarContract.Reminders.MINUTES, minutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }

            return@withContext eventId
        } catch (e: SecurityException) {
            throw CalendarPermissionException(CalendarAccess.WriteOnly, cause = e)
        } catch (e: Exception) {
            throw CalendarOperationFailedException(CalendarOperation.Insert, e.message ?: "Insert failed", cause = e)
        }
    }

    @OptIn(ExperimentalTime::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun queryEvents(query: CalendarQuery): List<SystemCalendarEvent> = withContext(Dispatchers.IO) {
        query.validate()
        ensureReadPermission()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, query.from.toEpochMilliseconds())
        ContentUris.appendId(builder, query.to.toEpochMilliseconds())

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END
        )

        val result = mutableListOf<SystemCalendarEvent>()

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                val eventIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val calIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val descIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)

                while (cursor.moveToNext()) {
                    val calendarId = cursor.getString(calIdIdx)
                    if (query.calendarIds.isNotEmpty() && calendarId !in query.calendarIds) continue

                    result += SystemCalendarEvent(
                        id = cursor.getLong(eventIdIdx).toString(),
                        calendarId = calendarId,
                        title = cursor.getString(titleIdx).orEmpty(),
                        notes = cursor.getString(descIdx),
                        location = cursor.getString(locIdx),
                        start = Instant.fromEpochMilliseconds(cursor.getLong(beginIdx)),
                        end = Instant.fromEpochMilliseconds(cursor.getLong(endIdx))
                    )
                }
            }
        } catch (e: SecurityException) {
            throw CalendarPermissionException(CalendarAccess.ReadWrite, cause = e)
        } catch (e: Exception) {
            throw CalendarOperationFailedException(CalendarOperation.Query, e.message ?: "Query failed", cause = e)
        }

        return@withContext result
    }

    @OptIn(ExperimentalTime::class)
    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun updateEvent(event: SystemCalendarEvent) = withContext(Dispatchers.IO) {
        event.validate()
        ensureWritePermission()

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, parseEventId(event.id))

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.notes)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.start.toEpochMilliseconds())
            put(CalendarContract.Events.DTEND, event.end.toEpochMilliseconds())
        }

        try {
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows == 0) {
                throw CalendarEventNotFoundException(event.id)
            }
        } catch (e: SecurityException) {
            throw CalendarPermissionException(CalendarAccess.WriteOnly, cause = e)
        } catch (e: Exception) {
            throw CalendarOperationFailedException(CalendarOperation.Update, e.message ?: "Update failed", cause = e)
        }
    }

    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun deleteEvent(eventId: String) = withContext(Dispatchers.IO) {
        ensureWritePermission()

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, parseEventId(eventId))

        try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows == 0) {
                throw CalendarEventNotFoundException(eventId)
            }
        } catch (e: SecurityException) {
            throw CalendarPermissionException(CalendarAccess.WriteOnly, cause = e)
        } catch (e: Exception) {
            throw CalendarOperationFailedException(CalendarOperation.Delete, e.message ?: "Delete failed", cause = e)
        }
    }

    @Throws(SystemCalendarException::class, CancellationException::class)
    actual suspend fun openEvent(eventId: String): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, parseEventId(eventId))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
        }

        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val canHandle = intent.resolveActivity(context.packageManager) != null
        if (!canHandle) {
            return false
        }

        context.startActivity(intent)
        return true
    }

    @Deprecated("Android cannot request calendar permissions without a host Activity. Request permissions in the app and call currentPermission().")
    actual suspend fun requestWritePermission(): CalendarPermissionStatus {
        return currentPermission()
    }

    @Deprecated("Android cannot request calendar permissions without a host Activity. Request permissions in the app and call currentPermission().")
    actual suspend fun requestReadWritePermission(): CalendarPermissionStatus {
        return currentPermission()
    }

    actual suspend fun currentPermission(): CalendarPermissionStatus = withContext(Dispatchers.IO) {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)

        return@withContext when {
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED -> CalendarPermissionStatus.Granted
            write == PackageManager.PERMISSION_GRANTED -> CalendarPermissionStatus.WriteOnly
            else -> CalendarPermissionStatus.Denied
        }
    }

    private fun ensureWritePermission() {
        if (!isPermissionDeclared(Manifest.permission.WRITE_CALENDAR)) {
            throw CalendarPermissionNotDeclaredException(
                platform = CalendarPlatform.Android,
                message = "android.permission.WRITE_CALENDAR is not declared in AndroidManifest.xml"
            )
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            throw CalendarPermissionException(CalendarAccess.WriteOnly)
        }
    }

    private fun ensureReadPermission() {
        if (!isPermissionDeclared(Manifest.permission.READ_CALENDAR)) {
            throw CalendarPermissionNotDeclaredException(
                platform = CalendarPlatform.Android,
                message = "android.permission.READ_CALENDAR is not declared in AndroidManifest.xml"
            )
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            throw CalendarPermissionException(CalendarAccess.ReadWrite)
        }
    }

    private fun isPermissionDeclared(permission: String): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            packageInfo.requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun parseEventId(id: String): Long =
        id.toLongOrNull() ?: throw CalendarEventNotFoundException(id)

    private fun findDefaultCalendarId(): String? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)).toString()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
