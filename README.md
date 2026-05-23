![Maven Central Version](https://img.shields.io/maven-central/v/be.vandeas/kalendar-kit)
[![Publish](https://github.com/LotuxPunk/KalendarKit/actions/workflows/publish.yml/badge.svg)](https://github.com/LotuxPunk/KalendarKit/actions/workflows/publish.yml)

# KalendarKit

## What is it?

KalendarKit is a Compose Multiplatform library for working with system calendars on Android and iOS.
It supports both:
- opening native system UI to let users add/review events
- programmatic calendar operations (insert, query, update, delete, open event)

## Example

<table>
  <tr>
    <td align="center"><b>Android</b></td>
    <td align="center"><b>iOS</b></td>
  </tr>
  <tr>
    <td><img src="assets/android.gif" width="300"/></td>
    <td><img src="assets/ios.gif" width="300"/></td>
  </tr>
</table>

## Installation

Add dependency in `commonMain`:

```kotlin
commonMain.dependencies {
    implementation("be.vandeas:kalendar-kit:1.1.0")
}
```

Or with version catalog:

```toml
[versions]
kalendar-kit = "1.1.0"

[libraries]
kalendar-kit = { module = "be.vandeas:kalendar-kit", version.ref = "kalendar-kit" }
```

## Platform setup

### Android

#### Manifest permissions

For programmatic read/write operations, declare:

```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
```

If you use system insert UI (`openAddEvent`), keep calendar app query declaration:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.INSERT" />
        <data android:mimeType="vnd.android.cursor.dir/event" />
    </intent>
</queries>
```

#### Initialization

```kotlin
val manager = Platform.calendarEventManager
manager.setup(context)
```

### iOS

#### Info.plist keys

Declare calendar usage descriptions in `Info.plist`:

```xml
<key>NSCalendarsUsageDescription</key>
<string>This app needs calendar access to read and manage events.</string>
<key>NSCalendarsFullAccessUsageDescription</key>
<string>This app needs calendar access to read and manage events.</string>
<key>NSCalendarsWriteOnlyAccessUsageDescription</key>
<string>This app needs calendar access to add events.</string>
```

#### Initialization

Set the presenting view controller before opening system event UI:

```swift
Platform.shared.calendarEventManager.setPresentingViewController(viewController: presentingVC)
```

## Usage

### 1. Open native add-event UI

```kotlin
val event = Event(
    title = "Team Meeting",
    startDate = now.toLocalDateTime(TimeZone.currentSystemDefault()),
    endDate = (now + 1.hours).toLocalDateTime(TimeZone.currentSystemDefault()),
    notes = "Discuss sprint goals",
    location = "Room B",
    url = "https://example.com"
)

val shown = manager.openAddEvent(event)
```

### 2. Programmatic CRUD

```kotlin
val id = manager.insertEvent(
    CalendarEventDraft(
        title = "Planning",
        start = now + 1.days,
        end = now + 1.days + 1.hours,
        notes = "Quarter planning",
        alarmMinutesBefore = listOf(15)
    )
)

val events = manager.queryEvents(
    CalendarQuery(
        from = now - 7.days,
        to = now + 7.days
    )
)

manager.updateEvent(
    SystemCalendarEvent(
        id = id,
        calendarId = null,
        title = "Planning (Updated)",
        start = now + 2.days,
        end = now + 2.days + 1.hours,
        notes = "Updated agenda",
        location = "Room C"
    )
)

manager.openEvent(id)
manager.deleteEvent(id)
```

### 3. Permission APIs

```kotlin
val status = manager.currentPermission()

val writeStatus = manager.requestWritePermission()
val readWriteStatus = manager.requestReadWritePermission()
```

`CalendarPermissionStatus` values:
- `NotDetermined`
- `Granted`
- `Denied`
- `Restricted`
- `WriteOnly`
- `Unknown`

## Exceptions

All domain exceptions inherit from `SystemCalendarException`:
- `CalendarPermissionException(requiredAccess: CalendarAccess)`:
  missing runtime permission for the requested operation (`WriteOnly` or `ReadWrite`).
- `CalendarPermissionNotDeclaredException(platform: CalendarPlatform)`:
  required permission declaration is missing from platform config (`AndroidManifest.xml` / `Info.plist`).
- `CalendarUnavailableException`:
  system calendar provider/app is unavailable on the current device.
- `CalendarEventNotFoundException(eventId: String)`:
  target event does not exist (commonly for `updateEvent`, `deleteEvent`, `openEvent`).
- `CalendarValidationException`:
  invalid input payload (for example blank title, invalid time range, invalid URL, duplicated alarm minutes).
- `CalendarOperationFailedException(operation: CalendarOperation)`:
  platform call failed while executing an operation (`OpenAddEvent`, `Insert`, `Query`, `Update`, `Delete`).

Common API-level exception mapping:
- `openAddEvent(event)`:
  may throw `CalendarValidationException`, `CalendarPermissionNotDeclaredException`, `CalendarPermissionException`, `CalendarOperationFailedException`, `CalendarUnavailableException`.
- `insertEvent(draft)`:
  may throw `CalendarValidationException`, `CalendarPermissionNotDeclaredException`, `CalendarPermissionException`, `CalendarOperationFailedException`, `CalendarUnavailableException`.
- `queryEvents(query)`:
  may throw `CalendarValidationException`, `CalendarPermissionNotDeclaredException`, `CalendarPermissionException`, `CalendarOperationFailedException`, `CalendarUnavailableException`.
- `updateEvent(event)`:
  may throw `CalendarValidationException`, `CalendarEventNotFoundException`, `CalendarPermissionNotDeclaredException`, `CalendarPermissionException`, `CalendarOperationFailedException`, `CalendarUnavailableException`.
- `deleteEvent(eventId)`:
  may throw `CalendarEventNotFoundException`, `CalendarPermissionNotDeclaredException`, `CalendarPermissionException`, `CalendarOperationFailedException`, `CalendarUnavailableException`.
- `openEvent(eventId)`:
  may throw `CalendarEventNotFoundException` (platform-dependent), `CalendarOperationFailedException`, `CalendarUnavailableException`.

## Notes

- `createEvent(event)` is deprecated. Use `openAddEvent(event)`.
