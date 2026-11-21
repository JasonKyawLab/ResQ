package com.resq.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * True OLED black (#000000), not dark grey — on AMOLED panels (the
 * overwhelming majority of Android phones this app targets) a black
 * pixel is a pixel that draws no current. This app is dark-only by
 * design, not "dark theme as an option": every screen, not just the
 * default, exists to be run for hours on a dying battery.
 */
val OledBlack = Color(0xFF000000)
val SurfaceElevated = Color(0xFF0D0D0D)
val SurfaceCard = Color(0xFF141414)
val Divider = Color(0xFF262626)

val SignalRed = Color(0xFFFF3B30)
val SignalRedDim = Color(0xFF7A1D18)
val BeaconGreen = Color(0xFF30D158)
val BeaconAmber = Color(0xFFFFB800)

val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFF9A9A9A)
