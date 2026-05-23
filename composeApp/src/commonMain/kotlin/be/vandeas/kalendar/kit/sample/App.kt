@file:OptIn(kotlin.time.ExperimentalTime::class)

package be.vandeas.kalendar.kit.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.vandeas.kalendar.kit.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Composable
@Preview
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val calendarManager = Platform.calendarEventManager
        var lastInsertedId by remember { mutableStateOf<String?>(null) }
        var statusMessage by remember { mutableStateOf("Ready") }
        var queriedEvents by remember { mutableStateOf<List<SystemCalendarEvent>>(emptyList()) }

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .safeContentPadding()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "KalendarKit Sample",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Text(
                    text = statusMessage,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 1. System UI - Open Add Event
                Button(
                    onClick = {
                        val now = Clock.System.now()
                        val event = Event(
                            title = "System UI Event",
                            notes = "Opened via openAddEvent",
                            startDate = now.toLocalDateTime(TimeZone.currentSystemDefault()),
                            endDate = (now + 1.hours).toLocalDateTime(TimeZone.currentSystemDefault()),
                            location = "San Francisco",
                            url = "https://example.com"
                        )
                        scope.launch {
                            try {
                                val result = calendarManager.openAddEvent(event)
                                statusMessage = "Open Add Event: $result"
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Open System Add Event UI")
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

                // 2. Programmatic - Insert Event
                Button(
                    onClick = {
                        val now = Clock.System.now()
                        val draft = CalendarEventDraft(
                            title = "Programmatic Event",
                            start = now + 1.days,
                            end = now + 1.days + 2.hours,
                            notes = "Inserted programmatically",
                            location = "Remote",
                            alarmMinutesBefore = listOf(15, 60)
                        )
                        scope.launch {
                            try {
                                val id = calendarManager.insertEvent(draft)
                                lastInsertedId = id
                                statusMessage = "Inserted ID: $id"
                            } catch (e: CalendarPermissionException) {
                                statusMessage = "Permission denied. Please grant calendar access."
                            } catch (e: Exception) {
                                statusMessage = "Insert failed: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Insert Event Programmatically")
                }

                // 3. Programmatic - Query Events
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val now = Clock.System.now()
                                val events = calendarManager.queryEvents(
                                    CalendarQuery(
                                        from = now - 7.days,
                                        to = now + 7.days
                                    )
                                )
                                queriedEvents = events
                                statusMessage = "Found ${events.size} events in next 7 days"
                                if (events.isNotEmpty()) {
                                    lastInsertedId = events.first().id
                                }
                            } catch (e: Exception) {
                                statusMessage = "Query failed: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Query Events (Next 7 Days)")
                }

                // Display queried events
                if (queriedEvents.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Queried Events (Next 7 Days):",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        queriedEvents.forEach { event ->
                            val isSelected = event.id == lastInsertedId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { lastInsertedId = event.id },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(event.title, fontWeight = FontWeight.SemiBold)
                                    Text("ID: ${event.id}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${event.start.toLocalDateTime(TimeZone.currentSystemDefault())}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    event.location?.let {
                                        Text("Loc: $it", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

                // 4. Programmatic - Update Last Event
                Button(
                    enabled = lastInsertedId != null,
                    onClick = {
                        val id = lastInsertedId ?: return@Button
                        scope.launch {
                            try {
                                val now = Clock.System.now()
                                val updatedEvent = SystemCalendarEvent(
                                    id = id,
                                    calendarId = null,
                                    title = "Updated Event Title",
                                    start = now + 2.days,
                                    end = now + 2.days + 1.hours,
                                    notes = "Updated notes content",
                                    location = "New Location"
                                )
                                calendarManager.updateEvent(updatedEvent)
                                statusMessage = "Updated event: $id"
                                // Refresh list
                                val events = calendarManager.queryEvents(
                                    CalendarQuery(
                                        from = now - 7.days,
                                        to = now + 7.days
                                    )
                                )
                                queriedEvents = events
                            } catch (e: Exception) {
                                statusMessage = "Update failed: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Update Selected Event")
                }

                // 5. Programmatic - Delete Last Event
                Button(
                    enabled = lastInsertedId != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val id = lastInsertedId ?: return@Button
                        scope.launch {
                            try {
                                calendarManager.deleteEvent(id)
                                statusMessage = "Deleted event: $id"
                                lastInsertedId = null
                                // Refresh list
                                val now = Clock.System.now()
                                val events = calendarManager.queryEvents(
                                    CalendarQuery(
                                        from = now - 7.days,
                                        to = now + 7.days
                                    )
                                )
                                queriedEvents = events
                            } catch (e: Exception) {
                                statusMessage = "Delete failed: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Delete Selected Event")
                }

                // 6. System UI - Open Specific Event
                Button(
                    enabled = lastInsertedId != null,
                    onClick = {
                        val id = lastInsertedId ?: return@Button
                        scope.launch {
                            try {
                                val result = calendarManager.openEvent(id)
                                statusMessage = "Open Event result: $result"
                            } catch (e: Exception) {
                                statusMessage = "Open Event failed: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Open Selected Event in Calendar App")
                }

                // 7. Permission Status
                Button(
                    onClick = {
                        scope.launch {
                            val permission = calendarManager.currentPermission()
                            statusMessage = "Current Permission: $permission"
                        }
                    }
                ) {
                    Text("Check Permission Status")
                }
            }
        }
    }
}
