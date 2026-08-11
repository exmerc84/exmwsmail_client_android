package com.exmworkspace.exmwsmail.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.stringResource
import com.exmworkspace.exmwsmail.R
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.data.mail.FolderKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// Bottom app bar (FAB + backdrop + custom shape). Extracted from MailScreen.

@Composable
internal fun BottomBarBackdrop(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tintColor = MaterialTheme.colorScheme.surface
    val glassStyle = remember(tintColor) {
        HazeStyle(
            backgroundColor = tintColor,
            blurRadius = 14.dp,
            tints = listOf(HazeTint(tintColor.copy(alpha = 0.06f))),
        )
    }
    val glassShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    // Same top-rounded shape, but the bottom edge is pushed far past the box's real
    // bottom. Modifier.shadow casts elevation around the *outline*, so by sending the
    // bottom outline off-screen we get a clean shadow only above the top curve.
    val shadowShape = remember { TopRoundedExtendedShape(cornerRadius = 24.dp) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(elevation = 10.dp, shape = shadowShape, clip = false)
            .clip(glassShape)
            .hazeEffect(state = hazeState, style = glassStyle),
    ) {
        // Swallows taps that land on the bar's own background so they never reach the list
        // scrolling behind the glass.
        //
        // A sibling *underneath* the content, not a wrapper around it. As a wrapper this
        // consumed the bar's own children too: `clickable` decides a tap between the down and
        // the up, and an ancestor consuming those changes in the same pass made it abandon
        // the gesture. On the emulator's instantaneous synthetic taps it usually survived; on
        // a real phone, where a finger lingers and drifts a pixel, the module and compose
        // buttons took several presses to fire.
        //
        // Below the content in z-order, the buttons get the touch first and this never sees
        // it; only presses on empty bar area fall through to here.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
        )
        content()
    }
}

internal class TopRoundedExtendedShape(
    private val cornerRadius: Dp,
    private val bottomExtension: Dp = 200.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val extra = with(density) { bottomExtension.toPx() }
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height + extra),
                    topLeft = CornerRadius(r, r),
                    topRight = CornerRadius(r, r),
                    bottomLeft = CornerRadius.Zero,
                    bottomRight = CornerRadius.Zero,
                )
            )
        }
        return Outline.Generic(path)
    }
}

