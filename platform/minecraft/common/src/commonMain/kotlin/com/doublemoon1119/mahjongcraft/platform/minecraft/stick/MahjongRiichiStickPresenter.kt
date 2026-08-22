package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的正式立直棒（千分棒）呈現資料——分成兩層：這局在場上宣告中的（放在宣告者自己
 * 牌河旁），以及延續自前局、尚未被任何人收下的供託堆（跟本場棒疊在莊家供子區角落），見
 * [MahjongRiichiStickPresenter] KDoc。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property riichiSeatIndices 目前立直中的座位 index 集合（`MahjongRuleModule.isPlayerInRiichi`）；空集合
 * 代表這局目前沒有人立直宣告。
 * @property dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index——延續自前局的供託堆只在
 * 莊家角落顯示，跟積棒同一個角落。
 * @property comboStickCount 目前積棒（本場棒）支數，恆等於 `TableState.comboCount`——延續自前局的供託
 * 堆疊放時從這個支數之後接續，視覺上跟積棒同一疊。
 * @property pooledStickCount 延續自前局、尚未被任何人收下的供託堆支數；`0` 代表沒有延續的供託。
 */
data class MahjongRiichiStickPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val riichiSeatIndices: Set<Int>,
    val dealerSeatIndex: Int,
    val comboStickCount: Int,
    val pooledStickCount: Int,
)

/** 正式立直棒呈現請求的處理結果。 */
enum class MahjongRiichiStickPresentationResult {
    /** 已把這桌的立直棒更新為本次要呈現的座位集合（或 [MahjongRiichiStickPresentation.riichiSeatIndices] 為空、只清除舊立直棒）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成新立直棒 entity 失敗（例如世界拒絕 spawn）；已生成的部分會被回滾，舊立直棒維持不變。 */
    SPAWN_FAILED,
}

/**
 * 將權威立直棒呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 立直棒不再是「換局一律清空」——流局後沒被收下的立直棒會延續到下一局（見
 * [MahjongRiichiStickPresentation.pooledStickCount]），只有真正被贏家收下、或整場對局結束時才會消失。
 * 這局在場上宣告中的（[MahjongRiichiStickPresentation.riichiSeatIndices]）跟延續自前局的供託堆
 * （[MahjongRiichiStickPresentation.pooledStickCount]）各自獨立更新，跟
 * [MahjongScoringStickPresenter]（積棒，綁在牌牆生成時間點）也是各自獨立的呈現流程。
 *
 * 立直棒沒有 domain 層身分（不像牌有 `IdentifiedTile.id`），比照 [MahjongScoringStickPresenter] 的
 * 按需生成模式：每次 [present] 都用 vanilla entity 隨機 UUID 生成本次要呈現的全部立直棒（宣告中＋延續
 * 供託堆），新的全部生成成功後才清除舊的。
 */
interface MahjongRiichiStickPresenter {
    /** 在指定桌面呈現這桌目前的全部立直棒（宣告中＋延續供託堆）；兩者皆為空時等同只清除舊立直棒。 */
    fun present(presentation: MahjongRiichiStickPresentation): MahjongRiichiStickPresentationResult

    /** 清除指定桌子目前的正式立直棒；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
