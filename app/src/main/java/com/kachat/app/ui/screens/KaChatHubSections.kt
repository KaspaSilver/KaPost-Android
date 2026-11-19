package com.kachat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors

// KaChat's Kaspa Hub sections are gone from KaPost. The .kachat marketplace (KachatMarketScreen),
// its claim, listing, buy and offer screens, the .kachat setup guide (KachatSetupGuideScreen,
// KnsWizardBottomBar) and the per-address .kachat lists were removed with iOS b78fdb0: .kachat is
// display only, and claiming is KaPay's (owner's call, 2026-11-18). What is left is the underline
// tab bar the action sheets still draw.

/**
 * iOS's UnderlineTabBar: equal-width tabs, each label 15pt bold on one line - the selected one in
 * the accent colour, the rest at half strength - shrinking to fit down to 75% rather than being
 * cut ("Marketplace" stays whole), 12 above it, a 2.5pt accent underline 8 below it under the
 * selected tab, and a hairline under the whole bar.
 */
@Composable
internal fun HubUnderlineTabBar(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            titles.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            role = androidx.compose.ui.semantics.Role.Tab,
                        ) { onSelect(index) }
                        .semantics { selected = isSelected },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KachatFitText(
                        title,
                        color = if (isSelected) KaspaTeal else KaspaTeal.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        minScale = 0.75f,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(if (isSelected) KaspaTeal else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
    }
}
