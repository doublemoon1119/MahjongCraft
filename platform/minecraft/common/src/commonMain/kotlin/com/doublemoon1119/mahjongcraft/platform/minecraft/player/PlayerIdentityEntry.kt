package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import kotlinx.serialization.Serializable

/**
 * 真人玩家的呈現身分，UUID 仍是權威識別。
 *
 * @property playerId 玩家的 UUID 字串。
 * @property name 伺服器保存的最後已知普通名稱。
 */
@Serializable
data class PlayerIdentityEntry(val playerId: String, val name: String)
