package dev.imkdw.claudewatch.testing

import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters

val watchDevice: DeviceParameters = DeviceParameters.Builder()
    .setScreenWidthDp(192)
    .setScreenHeightDp(192)
    .setScreenDensity(2.0f)
    .setFontScale(1f)
    .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
    .build()
