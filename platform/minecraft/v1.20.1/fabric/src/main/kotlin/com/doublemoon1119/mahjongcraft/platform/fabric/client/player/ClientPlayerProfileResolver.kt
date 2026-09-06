package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientThreadCoroutineDispatcher
import com.mojang.authlib.GameProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.MinecraftClient
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Player list 查不到 profile（多半是玩家已離線）時，非同步向 Mojang session server 查詢真實名稱與
 * 皮膚材質；同一個 UUID 一個 client 生命週期內只會真正發送一次查詢，查詢仍在進行或最終查無此人都回傳
 * `null`，呼叫端不需要分辨兩者，統一當作「暫時沒有資料」走 fallback 顯示即可。offline-mode 帳號的 UUID
 * 不對應真實 Mojang 帳號，查詢會直接落空，這種情況同樣回傳 `null`。
 */
@Single
class ClientPlayerProfileResolver(
    private val scope: ClientCoroutineScope,
    private val clientDispatcher: ClientThreadCoroutineDispatcher,
) {
    private val resolved = mutableMapOf<Uuid, GameProfile>()
    private val requested = mutableSetOf<Uuid>()

    /** 立即回傳目前已知的結果；還沒查完或確定查無此人都是 `null`。 */
    fun resolvedProfile(playerId: Uuid): GameProfile? = resolved[playerId]

    /** 如果這個 UUID 還沒查過也沒有查詢正在進行，觸發一次背景查詢；必須從主執行緒呼叫。 */
    fun requestResolve(playerId: Uuid) {
        if (playerId in resolved || !requested.add(playerId)) return
        val sessionService = MinecraftClient.getInstance().sessionService
        scope.launch {
            val profile = runCatching { sessionService.fillProfileProperties(GameProfile(playerId.toJavaUuid(), ""), false) }
                .getOrNull()
                ?.takeIf { it.name.isNotEmpty() }
            if (profile != null) {
                withContext(clientDispatcher) { resolved[playerId] = profile }
            }
        }
    }
}
