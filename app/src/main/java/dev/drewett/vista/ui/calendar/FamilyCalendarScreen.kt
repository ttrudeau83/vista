package dev.drewett.vista.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.drewett.vista.data.calendar.FamilyCalendarRepository
import dev.drewett.vista.data.calendar.FamilyEvent
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.activity.compose.BackHandler

@Composable
fun FamilyCalendarPanel(modifier: Modifier = Modifier, use24Hour: Boolean = false, onOpen: () -> Unit) {
    val repository = rememberCalendarRefresh()
    val state by repository.state.collectAsStateWithLifecycle()
    val weekEnd = LocalDate.now().plusDays(21).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val upcoming = state.events.filter { it.endMillis > System.currentTimeMillis() && it.startMillis < weekEnd }.take(7)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(modifier.height(190.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(12.dp))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Family · Upcoming", style = MaterialTheme.typography.titleMedium)
            when {
                !state.configured -> Text("Connect your calendar", style = MaterialTheme.typography.bodyMedium)
                upcoming.isEmpty() -> Text(if (state.loading) "Loading events…" else "No events in the next three weeks", style = MaterialTheme.typography.bodyMedium)
                else -> upcoming.forEach { event ->
                    Text(eventTime(event, use24Hour, true) + " · " + event.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.stale) Text("Saved offline", style = MaterialTheme.typography.labelSmall)
        }
    }
    }
}

@Composable
fun FamilyCalendarScreen(onDone: () -> Unit, use24Hour: Boolean, model: FamilyCalendarViewModel = viewModel()) {
    rememberCalendarRefresh()
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var configure by remember { mutableStateOf(false) }
    var feed by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { doneFocus.requestFocus() }
    BackHandler { if (configure) { configure = false; feed = "" } else onDone() }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Family calendar", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = { configure = !configure }) { Text(if (configure) "Month view" else "Calendar settings") }
            Button(onClick = { if (configure) { configure = false; feed = "" } else onDone() }, modifier = Modifier.focusRequester(doneFocus)) { Text(if (configure) "Back" else "Done") }
        }
        if (configure || !state.configured) {
            Text("In Google Calendar, open Settings → Family → Integrate calendar. Enter the Secret address in iCal format. Keep this link private.", style = MaterialTheme.typography.bodyLarge)
            FeedField(feed) { feed = it }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { model.connect(feed); feed = "" }, enabled = !busy && feed.isNotBlank()) { Text("Connect Family calendar") }
                if (state.configured) Button(onClick = { feed = ""; model.disconnect() }, enabled = !busy) { Text("Disconnect and clear events") }
            }
            Text(if (busy) "Working…" else message ?: "Read-only calendar access. Your private link is encrypted on this device.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Button(onClick = { month = month.minusMonths(1); selected = month.atDay(1) }, enabled = month > YearMonth.now().minusMonths(1)) { Text("Previous") }
                        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                        Button(onClick = { month = month.plusMonths(1); selected = month.atDay(1) }, enabled = month < YearMonth.now().plusMonths(6)) { Text("Next") }
                    }
                    Row(Modifier.fillMaxWidth()) { listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEach { Text(it, modifier = Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.labelMedium) } }
                    val first = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
                    repeat(6) { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(7) { day ->
                                val date = first.plusDays((week * 7 + day).toLong())
                                val count = state.events.count { it.occursOn(date) }
                                Button(onClick = { selected = date }, enabled = YearMonth.from(date) == month,
                                    modifier = Modifier.weight(1f).height(40.dp)) {
                                    Text((if (date == selected) "•" else "") + date.dayOfMonth + if (count > 0) "·" else "", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selected.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = model::refresh, enabled = !busy && !state.loading) { Text("Refresh") }
                    if (state.stale) Text("Saved events · offline", style = MaterialTheme.typography.bodyMedium)
                    val events = state.events.filter { it.occursOn(selected) }
                    if (events.isEmpty()) Text("No events on this day", style = MaterialTheme.typography.bodyLarge)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(events, key = { it.id }) { event ->
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).padding(12.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium)
                                Text(eventTime(event, use24Hour, false), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun rememberCalendarRefresh(): FamilyCalendarRepository {
    val context = LocalContext.current
    val repository = remember { FamilyCalendarRepository.get(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { repository.refresh(); delay(15 * 60_000) }
        }
    }
    return repository
}

@Composable
private fun FeedField(value: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(value = value, onValueChange = onChange, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.background(MaterialTheme.colorScheme.surface,shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border, shape).padding(16.dp),
        decorationBox = { field -> Box { if (value.isBlank()) Text("Private HTTPS iCal link", style = MaterialTheme.typography.bodyLarge); field() } })
}

private fun eventTime(event: FamilyEvent, use24Hour: Boolean, includeDate: Boolean): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zone)
    val date = if (includeDate) start.format(DateTimeFormatter.ofPattern("MMM d",Locale.getDefault())) + " · " else ""
    if (event.allDay) return date + "All day"
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    return date + start.format(DateTimeFormatter.ofPattern(pattern,Locale.getDefault()))
}
