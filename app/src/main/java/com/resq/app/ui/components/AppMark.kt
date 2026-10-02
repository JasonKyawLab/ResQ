package com.resq.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resq.app.R

/**
 * The app's mark (the SOS morse symbol), reusable wherever the wordmark
 * needs a visual anchor next to it — e.g. the Sender screen's header.
 *
 * Draws [R.drawable.ic_app_mark], a copy of the launcher icon's mark
 * cropped tight to its own bounding box, not the launcher icon itself:
 * the launcher version carries deliberate padding for the adaptive-icon
 * safe zone (so OS masking never clips it), which is exactly wasted
 * space once this is a flat in-app image with no masking to worry
 * about — it just reads as a large, unwanted gap to whatever sits next
 * to it. No background/clip here either, for the same reason: this
 * sits directly on the screen's own pure-black background, so any
 * backing tone or shape would show as an unintended box around it.
 */
@Composable
fun AppMark(modifier: Modifier = Modifier, height: Dp = 28.dp) {
    val width = height * (44f / 38f) // matches ic_app_mark's own 44x38 aspect ratio
    Image(
        painter = painterResource(R.drawable.ic_app_mark),
        contentDescription = null,
        modifier = modifier.width(width).height(height),
    )
}
