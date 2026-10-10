package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的開局發牌動畫呈現資料——只涵蓋立牌本身，不含摸牌位／副露／角落物件：開局當下沒有人已經
 * 摸牌、也沒有任何副露。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property handTileIdsBySeatIndex 每個座位最終手牌的完整牌 Uuid 列表（依 [MahjongPlayerAreaPresentation.standingTileIds]
 * 同一套「加入時間軸、不是畫面左右順序」的慣例），鍵為座位 index——這份順序只決定發牌動畫本身
 * （哪張牌在哪一批抵達、抵達當下落在哪一格），翻牌後最終要停在哪一格改看 [postFlipHandTileIdsBySeatIndex]。
 * @property postFlipHandTileIdsBySeatIndex 每個座位翻牌完成那一刻起、牌實際該停留的最終牌 Uuid 順序，
 * 鍵為座位 index——沒有啟用自動整理手牌的座位這裡跟 [handTileIdsBySeatIndex] 內容相同（翻牌後原地不動）；
 * 有啟用的座位這裡是已經整理過的順序，讓那個座位的牌翻起來之後立刻多一步瞬間移動到整理後的格位，發牌
 * 動畫本身（起飛／落下／翻牌的節奏與順序）完全不受影響。
 * @property dealerSeatIndex 本局莊家座位 index，決定發牌從哪個座位開始輪。
 * @property cornerWidthBySeatIndex 開局當下每個座位副露角落被佔用的寬度，鍵為座位 index；沒有列出的座位
 * 視為 `0.0`。理由同 [MahjongPlayerAreaPresentation.cornerWidth]。
 * @property dealBatchSizes 依序播放的批次大小列表，見 `MahjongRuleConfig.dealBatchSizes()`；呼叫端
 * 不驗證總和是否等於各座位手牌張數，由該函式自己保證。
 * @property extraLeadDelayTicks 這次發牌動畫在每張牌自己的動畫佇列最前面該多等待的 tick 數（等牌牆
 * 掉落動畫、擲骰動畫都播完才輪到發牌），折算進每一張牌自己的佇列（一個 `AnimationStep.Wait` step），
 * 不是外層再包一層延遲呼叫本身——理由見 `AnimatedMahjongEntity` KDoc：呼叫端延遲呼叫這個方法本身
 * 沒辦法撐過伺服器重啟，只有掛在 entity 自己身上的佇列才可以。
 */
data class MahjongInitialDealPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val dealerSeatIndex: Int,
    val cornerWidthBySeatIndex: Map<Int, Double>,
    val dealBatchSizes: List<Int>,
    val extraLeadDelayTicks: Int = 0,
)
