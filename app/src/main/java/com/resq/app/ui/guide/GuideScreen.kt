package com.resq.app.ui.guide

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resq.app.R
import com.resq.app.ui.theme.OledBlack
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.SurfaceCard
import com.resq.app.ui.theme.TextPrimary
import com.resq.app.ui.theme.TextSecondary

/**
 * The one guide, shown full-screen twice: once on first launch (button "I understand") and again from
 * Settings → How ResQ works (button "Back"). Both use the same text, so they never disagree.
 */
@Composable
fun GuideScreen(firstLaunch: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(OledBlack).statusBarsPadding().navigationBarsPadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (firstLaunch) R.string.guide_first_title else R.string.guide_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
            GuideCard(R.string.guide_intro_sends_title, listOf(R.string.guide_intro_sends), startOpen = firstLaunch)
            GuideCard(R.string.guide_intro_range_title, listOf(R.string.guide_intro_range), startOpen = firstLaunch)
            GuideCard(
                R.string.guide_steps_title,
                listOf(R.string.guide_step_1, R.string.guide_step_2, R.string.guide_step_3),
                numbered = true,
            )
            GuideCard(
                R.string.guide_help_title,
                listOf(R.string.guide_help_1, R.string.guide_help_2, R.string.guide_help_3, R.string.guide_help_4),
            )
            GuideCard(
                R.string.guide_updates_title,
                listOf(
                    R.string.guide_updates_1,
                    R.string.guide_updates_2,
                    R.string.guide_updates_3,
                    R.string.guide_updates_4,
                    R.string.guide_updates_5,
                ),
            )
            GuideCard(
                R.string.guide_helper_title,
                listOf(R.string.guide_helper_1, R.string.guide_helper_2, R.string.guide_helper_3),
            )
            GuideCard(R.string.guide_hops_title, listOf(R.string.guide_hops))
            GuideCard(
                R.string.guide_ready_title,
                listOf(R.string.guide_ready_1, R.string.guide_ready_2, R.string.guide_ready_3, R.string.guide_ready_4),
            )
        }
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = SignalRed, contentColor = TextPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(if (firstLaunch) R.string.guide_button_understand else R.string.guide_button_back),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

/**
 * A section that shows only its title until tapped, so the whole guide fits on one screen and a section
 * can be found at a glance. On first launch the two safety sections start open, so what ResQ sends and
 * what it cannot do are always read before the button.
 */
@Composable
private fun GuideCard(
    @StringRes title: Int,
    lines: List<Int>,
    numbered: Boolean = false,
    startOpen: Boolean = false,
) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "guide-chevron")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.rotate(turn),
            )
        }
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEachIndexed { index, line ->
                    val text = stringResource(line)
                    Text(
                        if (numbered) "${index + 1}. $text" else text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
