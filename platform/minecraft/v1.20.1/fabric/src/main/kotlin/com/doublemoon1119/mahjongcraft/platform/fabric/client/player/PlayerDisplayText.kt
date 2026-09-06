package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.text.Text

/**
 * 玩家名稱唯一決定「查不到名字時要顯示什麼」的地方——已知名字直接顯示，`null`（尚未解析或確定查無
 * 此人）一律顯示成多國語系的「離線玩家」，供所有讀取 [com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry.playerName]
 * 的畫面共用，避免各自重複判斷。
 */
fun resolvedPlayerNameText(name: String?): Text = name?.let(Text::literal) ?: Text.translatable(MinecraftRoomScreenKeys.OFFLINE_PLAYER)
