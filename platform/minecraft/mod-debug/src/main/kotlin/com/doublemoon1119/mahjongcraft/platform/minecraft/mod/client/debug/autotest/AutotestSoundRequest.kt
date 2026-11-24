package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import kotlin.time.Duration

/**
 * 自動測試期間一次被攔下、沒有實際播放的聲音要求。
 *
 * @property soundId 聲音事件的識別碼。
 * @property at 自動測試開始後多久發生。
 */
internal data class AutotestSoundRequest(val soundId: String, val at: Duration)
