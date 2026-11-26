package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import net.minecraft.Util
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** @return Minecraft 用於介面動畫的單調時鐘目前時間，以 [Util.getMillis] 為準。 */
internal fun measuringTimeNow(): Duration = Util.getMillis().milliseconds
