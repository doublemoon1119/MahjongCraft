package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import net.minecraft.text.Text
import kotlin.uuid.Uuid

/**
 * 將已解析的普通名稱轉成文字，真人名稱未知時顯示本地化的「離線玩家」。
 *
 * 需要角色翻譯的 AI 先由 [playerInfoDisplayName] 解析，不因缺少普通名稱就當成離線。
 *
 * @param name 已解析的玩家名稱；null 時使用離線玩家翻譯文字。
 * @return 玩家名稱或本地化的離線玩家文字。
 */
fun resolvedPlayerNameText(name: String?): Text = name?.let(Text::literal) ?: Text.translatable(MinecraftRoomScreenKeys.OFFLINE_PLAYER)

/**
 * 只有需要本地翻譯的 AI 名稱才交給角色解析器，其他已解析名稱及真人備援維持原值。
 *
 * @param player 玩家資訊面板中的公開成員。
 * @param resolveAiName 依已授權角色描述解析的本地名稱。
 * @return 本地可顯示的玩家名稱。
 */
fun playerInfoDisplayName(player: MahjongPlayerInfoEntry, resolveAiName: (Uuid) -> String): String = if (player.isAi && player.playerName == null) resolveAiName(player.playerId) else resolvedPlayerNameText(player.playerName).string
