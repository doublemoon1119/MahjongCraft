package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 在開發環境覆寫某桌下一次和牌的展示提示。
 * 只修改 [WinCelebrationRequest] 的展示理由，不修改權威役種、翻符或分數；
 * 展示定義仍可影響演出是否暫停整桌操作。
 *
 * [arm] 只在開發環境接受設定；[consume] 取走即移除，不再檢查環境。
 * 必須在判斷演出是否阻塞整桌之前套用 [applyTo]，讓演出與阻塞設定一致。
 *
 * @property minecraftEnvironment 開發環境判斷來源。
 */
class DebugWinShowcaseOverride(
    private val minecraftEnvironment: MinecraftEnvironment,
) {
    /** 各牌桌尚未消耗的一次性展示理由識別碼。 */
    private val armedCuesByTableId = ConcurrentHashMap<Uuid, String>()

    /**
     * 替 [tableId] 武裝一次性覆寫。
     *
     * @param tableId 要覆寫下一次胡牌演出的牌桌。
     * @param cueKey 要使用的展示理由識別碼。
     * @return 是否成功武裝；非開發環境一律為 `false`。
     */
    fun arm(tableId: Uuid, cueKey: String): Boolean {
        if (!minecraftEnvironment.isDevelopment) return false
        armedCuesByTableId[tableId] = cueKey
        return true
    }

    /**
     * 取走並清除牌桌的一次性覆寫。
     *
     * @param tableId 牌桌識別碼。
     * @return 已設定的展示理由識別碼；沒有設定時為 null。
     */
    fun consume(tableId: Uuid): String? = armedCuesByTableId.remove(tableId)

    /**
     * 清除指定牌桌的一次性覆寫。
     *
     * @param tableId 對局結束或牌桌被破壞的牌桌識別碼。
     */
    fun clear(tableId: Uuid) {
        armedCuesByTableId.remove(tableId)
    }

    /**
     * 取得仍有一次性展示覆寫的桌子。
     *
     * @return 呼叫當下取得的識別碼集合副本；後續設定變更不會修改此集合。
     */
    fun armedTableIds(): Set<Uuid> = armedCuesByTableId.keys.toSet()

    /**
     * 若 [tableId] 有武裝，回傳把所有贏家的展示理由換成該 cue 的 [WinCelebrationRequest]；否則原樣回傳。
     *
     * 必須在 `blocksTable` 判定**之前**套用，否則 showcase 會播、卻不擋全桌，
     * 剛好把這個覆寫想驗證的那條路徑跳過去。
     *
     * @param tableId 演出所屬牌桌。
     * @param request 原始胡牌演出請求。
     * @return 消耗覆寫後的請求；沒有覆寫時回傳原請求。
     */
    fun applyTo(tableId: Uuid, request: WinCelebrationRequest): WinCelebrationRequest {
        val cue = consume(tableId) ?: return request
        return request.copy(winners = request.winners.map { winner -> winner.copy(cueIds = listOf(cue)) })
    }
}
