package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import com.doublemoon1119.mahjongcraft.flow.server.game.service.MatchAbsenceSettings
import kotlin.time.Duration

/**
 * 從目前生效的伺服器設定讀取 [MatchAbsenceSettings]，重新載入設定後立即套用。
 *
 * @property configState 目前生效的伺服器設定。
 */
class ConfiguredMatchAbsenceSettings(private val configState: MinecraftServerConfigState) : MatchAbsenceSettings {
    override val inactiveRoundLimit: Int
        get() = configState.current.inactiveRoundLimit

    override val offlinePauseLimit: Duration
        get() = configState.current.offlinePauseTimeout
}
