package com.resq.app.ui.guide

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
            GuideCard(R.string.guide_intro_sends_title, listOf(R.string.guide_intro_sends))
            GuideCard(R.string.guide_intro_range_title, listOf(R.string.guide_intro_range))
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

@Composable
private fun GuideCard(@StringRes title: Int, lines: List<Int>, numbered: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
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
