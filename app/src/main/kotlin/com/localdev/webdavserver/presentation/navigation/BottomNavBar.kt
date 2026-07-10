package com.localdev.webdavserver.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * iOS-style bottom tab bar: thin top separator, icon + label, no pill indicator.
 * Active tab uses a tinted icon + semibold label; inactive uses gray + regular.
 */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onlineUsers: Int,
    unreadErrors: Int,
    onTabSelected: (Tab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            androidx.compose.foundation.layout.Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 10.dp, bottom = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
            ) {
                Tab.entries.forEach { tab ->
                    val selected = currentRoute == tab.screen.route
                    val badge = when (tab) {
                        Tab.USERS -> onlineUsers
                        Tab.LOG -> unreadErrors
                        else -> 0
                    }
                    TabItem(
                        tab = tab,
                        selected = selected,
                        badgeCount = badge,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: Tab,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        BadgedBox(badge = {
            if (badgeCount > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(badgeCount.toString())
                }
            }
        }) {
            when {
                tab.iconRes != null -> Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(30.dp).alpha(if (selected) 1f else 0.9f)
                )
                tab.icon != null -> Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(30.dp).alpha(if (selected) 1f else 0.9f)
                )
            }
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
