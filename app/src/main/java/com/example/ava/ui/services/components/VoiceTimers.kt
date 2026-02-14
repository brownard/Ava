package com.example.ava.ui.services.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ava.R
import com.example.ava.esphome.voicesatellite.VoiceTimer
import com.example.ava.ui.services.ServiceViewModel
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

data class TimerState(
    val timers: List<VoiceTimer>,
    val now: Instant
)

@Composable
fun timerState(viewModel: ServiceViewModel = hiltViewModel()): TimerState {
    val timers by viewModel.voiceTimers.collectAsStateWithLifecycle(emptyList())

    // Shared ticker to ensure all cards update on the same frame,
    // Inactive if there's no running card to update or the screen is off.
    var now by remember { mutableStateOf(Clock.System.now()) }

    if (timers.any { it is VoiceTimer.Running }) {
        LaunchedEffect(timers) {
            while (true) {
                delay(1000)
                now = Clock.System.now()
            }
        }
    }

    return TimerState(timers, now)
}

fun LazyListScope.timerListSection(
    state: TimerState,
) {
    items(state.timers, key = { it.id }) { timer ->
        TimerCard(
            timer = timer,
            now = state.now,
            modifier = Modifier.animateItem()
        )
    }
}

@Composable
fun TimerCard(
    timer: VoiceTimer,
    now: Instant,
    modifier: Modifier = Modifier
) {
    val remainingDuration = timer.remainingDuration(now)
    val textColor = when (timer) {
        is VoiceTimer.Running -> MaterialTheme.colorScheme.onSurface
        is VoiceTimer.Paused -> MaterialTheme.colorScheme.outline
        is VoiceTimer.Ringing -> MaterialTheme.colorScheme.primary
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when (timer) {
                is VoiceTimer.Running -> MaterialTheme.colorScheme.surfaceContainerHigh
                is VoiceTimer.Paused -> MaterialTheme.colorScheme.surfaceContainerLow
                is VoiceTimer.Ringing -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(
                    when (timer) {
                        is VoiceTimer.Running -> R.drawable.timer_24px
                        is VoiceTimer.Paused -> R.drawable.timer_pause_24px
                        is VoiceTimer.Ringing -> R.drawable.notifications_active_24px
                    }
                ),
                contentDescription = "Timer icon",
                tint = textColor,
                modifier = Modifier.size(48.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = remainingDuration.toClock(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = textColor,
                    )
                }

                if (timer !is VoiceTimer.Ringing) {
                    LinearProgressIndicator(
                        progress = {
                            if (timer.totalDuration > Duration.ZERO) {
                                remainingDuration.div(timer.totalDuration).toFloat()
                            } else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = if (timer is VoiceTimer.Paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

fun Duration.toClock(): String = toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}