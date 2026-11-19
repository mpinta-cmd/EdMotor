package com.example.edmotor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edmotor.navigation.Routes

private data class EdMotorTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    EdMotorTab(Routes.HOME, "Inicio", Icons.Outlined.Home),
    EdMotorTab(Routes.SERVICES, "Servicios", Icons.Outlined.Build),
    EdMotorTab(Routes.PROFILE, "Mi Garaje", Icons.Outlined.DirectionsCar),
    EdMotorTab(Routes.APPOINTMENTS, "Citas", Icons.Outlined.CalendarMonth)
)

private val iconSize = 24.dp
private val tabHorizontalPadding = 4.dp
private val badgeGap = 4.dp
private val badgeHorizontalPadding = 5.dp
private val badgeVerticalPadding = 2.dp
private val dockPadding = 6.dp
private val tabGap = 3.dp
private const val maxBadgeDisplay = "99+"

@Composable
fun EdMotorBottomBar(
    currentRoute: String?,
    pendingOrderCount: Int = 0,
    pendingAppointmentCount: Int = 0,
    onNavigate: (String) -> Unit
) {
    val activeRoute = if (currentRoute == Routes.ORDER_MANAGEMENT) Routes.PROFILE else currentRoute
    val activeIndex = tabs.indexOfFirst { it.route == activeRoute }
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
    val badgeStyle = labelStyle.copy(fontWeight = FontWeight.Bold)
    val density = LocalDensity.current
    val iconLaneHeight = maxOf(28.dp, with(density) { badgeStyle.lineHeight.toDp() } + badgeVerticalPadding * 2)
    val tabsState = rememberLazyListState(initialFirstVisibleItemIndex = activeIndex.coerceAtLeast(0))

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val layout = tabsState.layoutInfo
            val isFullyVisible = layout.visibleItemsInfo.any { item ->
                item.index == activeIndex && item.offset >= layout.viewportStartOffset &&
                        item.offset + item.size <= layout.viewportEndOffset
            }
            if (!isFullyVisible) tabsState.animateScrollToItem(activeIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 5.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, colors.outline),
            shadowElevation = 5.dp
        ) {
            BoxWithConstraints(Modifier.padding(dockPadding)) {
                val minTabWidth = maxOf(48.dp, (maxWidth - tabGap * (tabs.size - 1)) / tabs.size)

                LazyRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    state = tabsState,
                    horizontalArrangement = Arrangement.spacedBy(tabGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(tabs, key = { it.route }) { tab ->
                        val count = when (tab.route) {
                            Routes.PROFILE -> pendingOrderCount
                            Routes.APPOINTMENTS -> pendingAppointmentCount
                            else -> 0
                        }
                        EdMotorTabButton(
                            tab = tab,
                            selected = tab.route == activeRoute,
                            count = count,
                            labelStyle = labelStyle,
                            badgeStyle = badgeStyle,
                            iconLaneHeight = iconLaneHeight,
                            modifier = Modifier.widthIn(min = minTabWidth).width(IntrinsicSize.Max),
                            onClick = { if (tab.route != currentRoute) onNavigate(tab.route) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EdMotorTabButton(
    tab: EdMotorTab,
    selected: Boolean,
    count: Int,
    labelStyle: TextStyle,
    badgeStyle: TextStyle,
    iconLaneHeight: Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tileColor by animateColorAsState(
        if (selected) colors.primaryContainer else Color.Transparent,
        label = "tabFill"
    )
    val contentColor by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        label = "tabContent"
    )
    val indicatorWidth by animateDpAsState(
        if (selected) 16.dp else 0.dp,
        label = "tabIndicator"
    )

    Column(
        modifier = modifier
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(tileColor)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics {
                if (count > 0) {
                    stateDescription = when {
                        tab.route == Routes.PROFILE && count == 1 -> "1 pedido pendiente"
                        tab.route == Routes.PROFILE -> "$count pedidos pendientes"
                        count == 1 -> "1 cita pendiente"
                        else -> "$count citas pendientes"
                    }
                }
            }
            .padding(horizontal = tabHorizontalPadding, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.heightIn(min = iconLaneHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = contentColor)
            if (count > 0) {
                Spacer(Modifier.width(badgeGap))
                Box(
                    modifier = Modifier
                        .background(colors.secondaryContainer, CircleShape)
                        .defaultMinSize(minWidth = 19.dp)
                        .padding(horizontal = badgeHorizontalPadding, vertical = badgeVerticalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = if (count > 99) maxBadgeDisplay else count.toString(),
                        style = badgeStyle.copy(color = colors.onSecondaryContainer),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.semantics { hideFromAccessibility() }
                    )
                }
            }
        }

        Text(
            text = tab.label,
            style = labelStyle,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(width = indicatorWidth, height = 2.dp)
                .background(colors.primary, CircleShape)
        )
    }
}
