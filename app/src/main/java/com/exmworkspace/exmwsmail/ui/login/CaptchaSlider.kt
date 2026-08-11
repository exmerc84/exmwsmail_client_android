package com.exmworkspace.exmwsmail.ui.login

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.exmworkspace.exmwsmail.data.remote.dto.CaptchaPointDto
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Outcome of a drag attempt that never left the device. */
sealed interface CaptchaGesture {
    data class Completed(val durationMs: Long, val points: List<CaptchaPointDto>) : CaptchaGesture
    /** The drag was human-driven but too fast/slow/sparse for the backend heuristics. */
    data class TooCrude(val reason: String) : CaptchaGesture
}

/**
 * Behavioural slider for the login captcha (§1.5).
 *
 * Points come straight from `MotionEvent` — including the batched historical samples —
 * because the backend rejects traces that look synthesised: it wants timing jitter, vertical
 * wobble and 8+ samples, all of which a real finger produces for free and a generated curve
 * does not. Nothing here smooths or resamples the input.
 *
 * The x axis is normalised onto the same logical track the web slider uses so the
 * "last x reached ≥90% of the travel" check lines up regardless of screen width.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CaptchaSlider(
    onGesture: (CaptchaGesture) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    solved: Boolean = false,
    label: String = "Desliza para verificar",
) {
    val density = LocalDensity.current
    val handleSize = 52.dp
    val handlePx = with(density) { handleSize.toPx() }

    var trackWidthPx by remember { mutableIntStateOf(0) }
    val handleX = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val points = remember { mutableListOf<CaptchaPointDto>() }
    var downTime by remember { mutableLongStateOf(0L) }

    val maxTravel = (trackWidthPx - handlePx).coerceAtLeast(1f)

    LaunchedEffect(solved) {
        if (solved) handleX.snapTo(maxTravel)
    }

    fun record(x: Float, y: Float, eventTime: Long) {
        val logicalX = ((x - handlePx / 2f).coerceIn(0f, maxTravel) / maxTravel * LOGICAL_TRAVEL)
        val point = CaptchaPointDto(
            x = logicalX.roundToInt(),
            y = y.roundToInt(),
            t = eventTime - downTime,
        )
        // At the cap, overwrite the tail rather than drop samples: the backend checks that
        // the LAST x reaches ≥90% of the travel, so the end of the trace must survive.
        if (points.size >= MAX_POINTS) {
            points[points.lastIndex] = point
        } else {
            points += point
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { trackWidthPx = it.width }
            .pointerInteropFilter { event ->
                if (!enabled || solved) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Only start when the finger lands on the handle.
                        if (event.x > handleX.value + handlePx * 1.5f) {
                            return@pointerInteropFilter false
                        }
                        points.clear()
                        downTime = event.eventTime
                        dragging = true
                        record(event.x, event.y, event.eventTime)
                        scope.launch { handleX.snapTo((event.x - handlePx / 2f).coerceIn(0f, maxTravel)) }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) return@pointerInteropFilter false
                        // Batched samples between frames — this is where the density that
                        // makes the trace look human comes from.
                        for (h in 0 until event.historySize) {
                            record(
                                event.getHistoricalX(h),
                                event.getHistoricalY(h),
                                event.getHistoricalEventTime(h),
                            )
                        }
                        record(event.x, event.y, event.eventTime)
                        scope.launch { handleX.snapTo((event.x - handlePx / 2f).coerceIn(0f, maxTravel)) }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!dragging) return@pointerInteropFilter false
                        dragging = false
                        record(event.x, event.y, event.eventTime)
                        val duration = event.eventTime - downTime
                        val reachedEnd = handleX.value >= maxTravel * COMPLETION_RATIO
                        val snapshot = points.toList()

                        if (!reachedEnd) {
                            scope.launch { handleX.animateTo(0f) }
                        } else {
                            val problem = validate(duration, snapshot)
                            if (problem != null) {
                                scope.launch { handleX.animateTo(0f) }
                                onGesture(CaptchaGesture.TooCrude(problem))
                            } else {
                                scope.launch { handleX.animateTo(maxTravel) }
                                onGesture(CaptchaGesture.Completed(duration, snapshot))
                            }
                        }
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (solved) "Verificado" else label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(handleX.value.roundToInt(), 0) }
                .padding(2.dp)
                .size(handleSize - 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (solved) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (solved) Icons.Default.Check
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (solved) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Mirrors the server-side heuristics so an obviously-doomed trace is rejected locally
 * instead of burning a challenge (they are single-use) on a guaranteed 400.
 */
private fun validate(durationMs: Long, points: List<CaptchaPointDto>): String? = when {
    durationMs < MIN_DURATION_MS -> "Deslizaste demasiado rápido, inténtalo más despacio"
    durationMs > MAX_DURATION_MS -> "Tardaste demasiado, inténtalo de nuevo"
    points.size < MIN_POINTS -> "Movimiento demasiado brusco, deslizá de forma continua"
    points.map { it.y }.distinct().size < 2 -> "Movimiento demasiado recto, inténtalo de nuevo"
    else -> null
}

private const val LOGICAL_TRAVEL = 268f
private const val COMPLETION_RATIO = 0.98f
private const val MIN_DURATION_MS = 250L
private const val MAX_DURATION_MS = 8_000L
private const val MIN_POINTS = 8
private const val MAX_POINTS = 400
