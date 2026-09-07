package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的正式供託棒（千分棒）呈現資料——分成兩層：這局場上宣告中的（放在宣告者自己
 * 牌河旁），以及延續自前局、尚未被任何人收下的供託堆（跟本場棒疊在莊家供子區角落），見
 * [MahjongStickPotPresenter] KDoc。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property declaredSeatIndices 目前有宣告中供託棒的座位 index 集合（例如日麻的
 * `MahjongRuleModule.isPlayerInRiichi`）；空集合代表這局目前沒有人宣告。
 * @property dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index——延續自前局的供託堆只在
 * 莊家角落顯示，跟積棒同一個角落。
 * @property comboStickCount 目前積棒（本場棒）支數，恆等於 `TableState.comboCount`——延續自前局的供託
 * 堆疊放時從這個支數之後接續，視覺上跟積棒同一疊。
 * @property pooledStickCount 延續自前局、尚未被任何人收下的供託堆支數；`0` 代表沒有延續的供託。
 */
data class MahjongStickPotPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val declaredSeatIndices: Set<Int>,
    val dealerSeatIndex: Int,
    val comboStickCount: Int,
    val pooledStickCount: Int,
)

/** 正式供託棒呈現請求的處理結果。 */
enum class MahjongStickPotPresentationResult {
    /** 已把這桌的供託棒更新為本次要呈現的座位集合（或 [MahjongStickPotPresentation.declaredSeatIndices] 為空、只清除舊供託棒）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成新供託棒 entity 失敗（例如世界拒絕 spawn）；已生成的部分會被回滾，舊供託棒維持不變。 */
    SPAWN_FAILED,
}

/**
 * 將權威供託棒呈現於 Minecraft 世界的版本 adapter 邊界——不假設任何特定規則模組，供託棒是否成立、
 * 由誰宣告完全交給呼叫端的 [MahjongStickPotPresentation] 決定。
 *
 * 供託棒不是「換局一律清空」——流局後沒被收下的供託棒會延續到下一局（見
 * [MahjongStickPotPresentation.pooledStickCount]），只有真正被贏家收下、或整場對局結束時才會消失。
 * 這局場上宣告中的（[MahjongStickPotPresentation.declaredSeatIndices]）跟延續自前局的供託堆
 * （[MahjongStickPotPresentation.pooledStickCount]）各自獨立更新，跟
 * [MahjongScoringStickPresenter]（積棒，綁在牌牆生成時間點）也是各自獨立的呈現流程。
 *
 * 供託棒沒有 domain 層身分（不像牌有 `IdentifiedTile.id`），比照 [MahjongScoringStickPresenter] 的
 * 按需生成模式：每次 [present] 都用 vanilla entity 隨機 UUID 生成本次要呈現的全部供託棒（宣告中＋延續
 * 供託堆），新的全部生成成功後才清除舊的。
 */
interface MahjongStickPotPresenter {
    /** 在指定桌面呈現這桌目前的全部供託棒（宣告中＋延續供託堆）；兩者皆為空時等同只清除舊供託棒。 */
    fun present(presentation: MahjongStickPotPresentation): MahjongStickPotPresentationResult

    /** 清除指定桌子目前的正式供託棒；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
