package com.resq.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resq.app.R
import com.resq.app.ui.theme.SurfaceCard

/**
 * The app's launcher mark (the SOS morse symbol), reusable wherever the
 * wordmark needs a visual anchor next to it — e.g. the Sender screen's
 * header. Draws [R.drawable.ic_launcher_foreground] directly rather than
 * `R.mipmap.ic_launcher`: the mipmap resolves to an `AdaptiveIconDrawable`
 * on API 26+, which `painterResource` doesn't reliably render, whereas
 * the plain foreground/background vector drawables are ordinary
 * VectorDrawables on every API level this app supports.
 */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(SurfaceCard),
    )
}
