package com.doublemoon1119.mahjongcraft.platform.fabric.server.player

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 在伺服器端解析一個真人玩家目前已知的名稱：優先看是否在線，不在線時退回 [net.minecraft.util.UserCache]
 * 這個本地、同步、不用連網的名字快取（伺服器曾經看過這個 UUID 就查得到，offline-mode 假 UUID 也適用，
 * 因為快取存的是伺服器自己記得的對應關係，不是 Mojang 官方資料）。兩者都查不到就回傳 `null`，交由呼叫
 * 端決定要顯示什麼 fallback 文字。
 */
fun resolveKnownPlayerName(serverHolder: FabricServerHolder, playerId: Uuid): String? = serverHolder.findPlayer(playerId)?.gameProfile?.name
    ?: serverHolder.current()?.userCache?.getByUuid(playerId.toJavaUuid())?.orElse(null)?.name
