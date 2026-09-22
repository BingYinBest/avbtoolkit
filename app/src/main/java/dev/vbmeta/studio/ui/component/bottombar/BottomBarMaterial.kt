package dev.vbmeta.studio.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.vbmeta.studio.R
import dev.vbmeta.studio.ui.LocalMainPagerState

@Composable
fun BottomBarMaterial() {
    val mainPagerState = LocalMainPagerState.current

    val items = listOf(
        Triple(R.string.tab_workbench, Icons.Filled.Construction, Icons.Outlined.Construction),
        Triple(R.string.tab_verify, Icons.Filled.Info, Icons.Outlined.Info),
        Triple(R.string.tab_keys, Icons.Filled.Key, Icons.Outlined.Key),
        Triple(R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    FlexibleBottomAppBar(
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val selected = mainPagerState.selectedPage == index
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    Icon(
                        if (selected) selectedIcon else unselectedIcon,
                        stringResource(label)
                    )
                },
                label = {
                    Text(
                        stringResource(label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
