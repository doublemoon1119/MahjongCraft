package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.util.Util
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** @return Minecraft 用於介面動畫的單調時鐘目前時間，以 [Util.getMeasuringTimeMs] 為準。 */
internal fun measuringTimeNow(): Duration = Util.getMeasuringTimeMs().milliseconds
