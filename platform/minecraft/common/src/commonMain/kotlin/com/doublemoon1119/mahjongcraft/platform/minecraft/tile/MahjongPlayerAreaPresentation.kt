package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的單一玩家「桌角區域」呈現資料——手牌、摸牌位、副露三者合併成一次呼叫，理由見
 * [MahjongPlayerAreaPresenter] KDoc。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatLayout 這桌玩家席位對應的固定桌面布局。
 * @property seatIndex 這位玩家在 `TableState.players` 的固定座位 index。
 * @property standingTileIds 這位玩家目前立牌張數，依加入手牌的時間軸排列（`Hand.tiles`，不含
 * [drawnTileId]；先加入的在前，例如非摸切捨牌時併入立牌的 `Hand.lastDrawn` 排在最後面），鍵為
 * [IdentifiedTile.id]；空清單代表這局結束，只需要清除舊牌。這個順序不是畫面上的左右順序——實作端會
 * 自行反過來對應到左右座標，讓最後加入的那張牌落在玩家自己右手邊，符合真實麻將摸牌後插入手牌的直覺
 * 方向，見 `FabricMahjongPlayerAreaPresenter.present` KDoc。
 * @property drawnTileId 這位玩家目前摸到、尚未併入立牌或打出的那張牌 Uuid（`Hand.lastDrawn`）；
 * `null` 代表目前沒有摸牌位要呈現。
 * @property melds 這位玩家目前所有副露，依宣告順序排列——第一組（最早宣告）位於副露區固定的桌角
 * 錨點讓開 [cornerWidth] 之後的位置，後續每組依序往玩家自己手牌方向排開，呼叫端不需要另外傳遞
 * 位置索引。
 * @property cornerWidth 這個座位副露角落被規則桌面物件佔用的寬度（見 `TablePropDescriber.cornerWidth`）；
 * 只用來讓手牌／副露正確讓開，這個 presenter 本身不負責那些物件的生成／清除，見 [TablePropPresenter]。
 * @property animateDrawnTile [drawnTileId] 非 `null` 時，是否要播放摸牌動畫（牌從牌牆原位面朝下起飛、
 * 短暫隱形傳送到摸牌位、傳送的同一瞬間切換成面向玩家的姿態、解除隱形後再落下）——姿態切換發生在
 * 隱形期間，玩家看不到旋轉過程，不像開局發牌動畫那樣需要落地後另外播放看得見的翻牌動畫。從牌牆摸牌（一般摸牌、
 * 槓與移出手牌後的補牌）該傳 `true`，其餘呼叫端維持預設 `false` 直接定格顯示，不重複播放動畫。同一次呈現也有
 * [animatedMeldClaimTileIds]／[animatedSetAsideTileIds] 時，摸牌等那些牌落地後才開始。
 * @property animatedMeldClaimTileIds 這次鳴牌/槓牌成立，需要播放「連續飛到副露區最終格位」動畫的牌
 * Uuid 集合——不像開局發牌動畫那樣中途隱形傳送，全程看得見飛行過程。呼叫端直接指名要動畫哪些牌，不是
 * 用「哪一組副露」推斷：吃/碰/明槓是整組一次成立的新副露，全部牌都新，直接傳整組；暗槓也是整組全新
 * 副露；但加槓是把新牌插進一組**既有**副露（`Hand.upgradeToAddedKan` 原地修改既有那組，不是加到
 * `melds` 尾端），只有新插入的那一張該動畫，既有三張碰的牌本來就已經在正確位置，不需要重新移動——用
 * 「最後一組」這種位置推斷會在加槓的那組副露不是清單最後一組時抓錯目標，因此改成明確指名。空集合
 * （預設值）代表這次沒有任何牌需要播放這個動畫，維持既有的瞬間顯示。
 * @property setAsideTileIds 這位玩家移出手牌、公開擺在桌上的牌（例如三人日麻拔出的北），依移出順序排列；
 * 接在最後一組副露之後直立正面朝上擺放，空清單（預設值）代表沒有。
 * @property animatedSetAsideTileIds [setAsideTileIds] 中這次剛移出手牌的牌，比照 [animatedMeldClaimTileIds]
 * 播放飛到桌上最終格位的動畫、放置音效與落地牌面提示；空集合（預設值）代表沒有，維持瞬間顯示。
 * @property followUpSteps 這次摸牌之後依序自動移出與補牌的步驟，依 [DrawFollowUpTimeline] 逐步播放；各步移出的牌也包含在
 * [setAsideTileIds]，第一步移出的是這次摸到的牌之一，每一步移出的牌在那一步開始時位於摸牌位，[drawnTileId] 是處理結束後
 * 留在摸牌位的牌。空清單（預設值）代表沒有。
 */
data class MahjongPlayerAreaPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val drawnTileId: Uuid?,
    val melds: List<MahjongMeldTileGroup>,
    val cornerWidth: Double,
    val animateDrawnTile: Boolean = false,
    val animatedMeldClaimTileIds: Set<Uuid> = emptySet(),
    val setAsideTileIds: List<Uuid> = emptyList(),
    val animatedSetAsideTileIds: Set<Uuid> = emptySet(),
    val followUpSteps: List<DrawFollowUpAnimationStep> = emptyList(),
)
