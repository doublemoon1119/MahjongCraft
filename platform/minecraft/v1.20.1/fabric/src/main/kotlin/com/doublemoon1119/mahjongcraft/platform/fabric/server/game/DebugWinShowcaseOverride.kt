package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationCue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 開發用的一次性役滿 showcase 覆寫：讓**下一次**該桌真實胡牌一定播放指定的 showcase cue。
 *
 * 存在理由：`RiichiWinCelebrationCueResolver` 只有役滿才回傳非 null 的 cue，而 `cue != null` 正是
 * 「這次胡牌會不會有需要觀看的 showcase」的判準。中途胡牌把「顯示什麼」與「是否阻塞全桌」分開建模
 * 之後，最難湊到、也最需要實機驗證的情境就是「役滿 showcase 期間擋住全桌 → showcase 結束後其他仍在
 * 局中的玩家立刻恢復可操作，而結算面板與收尾蓋牌動畫都不再阻塞」。真的湊出一副役滿來驗證這條路徑
 * 並不實際，因此提供這個覆寫。
 *
 * ## 只能動呈現，動不到權威資料
 *
 * 覆寫的對象是 [WinCelebrationRequest]，而這個型別**只**攜帶 `winningTileId`、`isTsumo` 與每位贏家的
 * `seatIndex` 與 [WinCelebrationCue]——沒有役種、沒有番數、沒有符數、沒有分數。分數結算走的是完全
 * 獨立的 `WinSettlementPresentationRequest`，權威分數更早就已經由規則模組算完並寫進
 * `TableState`。因此這個覆寫在結構上不可能改到權威役種、番數、分數或結算結果，它能改變的只有
 * 「要不要播 showcase、播哪一段」，以及隨之而來的阻塞判定。
 *
 * ## 一次性
 *
 * [consume] 取走即清除：武裝一次只影響下一次胡牌，不會讓該桌後續每一次胡牌都變成役滿演出。
 *
 * 只在 [MinecraftEnvironment.isDevelopment] 為 `true` 時可武裝；正式產物裡 [arm] 直接拒絕，
 * [consume] 永遠回傳 `null`。
 *
 * @property minecraftEnvironment 查詢目前是否為開發環境。
 */
@Single
class DebugWinShowcaseOverride(
    private val minecraftEnvironment: MinecraftEnvironment,
) {
    private val armedCuesByTableId = ConcurrentHashMap<Uuid, WinCelebrationCue>()

    /**
     * 替 [tableId] 武裝一次性覆寫。
     *
     * @return 是否成功武裝；非開發環境一律為 `false`。
     */
    fun arm(tableId: Uuid, cueKey: String): Boolean {
        if (!minecraftEnvironment.isDevelopment) return false
        armedCuesByTableId[tableId] = WinCelebrationCue(key = cueKey)
        return true
    }

    /** 取走並清除 [tableId] 的武裝；沒有武裝時回傳 `null`。 */
    fun consume(tableId: Uuid): WinCelebrationCue? = armedCuesByTableId.remove(tableId)

    /** 清除 [tableId] 的武裝；該桌對局結束或桌子被破壞時呼叫。 */
    fun clear(tableId: Uuid) {
        armedCuesByTableId.remove(tableId)
    }

    /** 目前仍有武裝的桌子，供指令回報影響範圍。 */
    fun armedTableIds(): Set<Uuid> = armedCuesByTableId.keys.toSet()

    /**
     * 若 [tableId] 有武裝，回傳把所有贏家的 cue 換成該 cue 的 [WinCelebrationRequest]；否則原樣回傳。
     *
     * 必須在 `hasWatchableShowcase`／`blocksTable` 判定**之前**套用，否則 showcase 會播、卻不擋全桌，
     * 剛好把這個覆寫想驗證的那條路徑跳過去。
     */
    fun applyTo(tableId: Uuid, request: WinCelebrationRequest): WinCelebrationRequest {
        val cue = consume(tableId) ?: return request
        return request.copy(winners = request.winners.map { winner -> winner.copy(cue = cue) })
    }
}
