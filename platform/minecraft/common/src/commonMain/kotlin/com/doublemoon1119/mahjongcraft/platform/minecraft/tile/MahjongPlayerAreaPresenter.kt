package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 單一副露的呈現資料，只帶位置呈現需要的最小資訊，不攜帶實際牌面——管理中牌張的牌面完全交給 client
 * 端依可見性快照另外呈現，平台呈現層只需要知道怎麼擺位置。
 *
 * @property type 副露種類，決定是否有需要側身呈現的鳴取牌。
 * @property tileIds 組成這組副露的所有牌 Uuid，依宣告時的原始順序排列。
 * @property calledTileId [tileIds] 之中鳴取自他家、需側身呈現的那張牌 Uuid；暗槓沒有鳴牌來源時為
 * `null`。
 * @property sourceDirection 鳴取來源的相對方位，決定 [calledTileId] 在組內橫放的位置（左／中／右）；
 * 暗槓沒有鳴牌來源時為 [RelativeDirection.Self]。
 * @property allTilesFaceDown 只在 [type] 為 [MeldType.CLOSED_KAN] 時有意義：該規則是否連暗槓身份都
 * 不公開，此時四張牌全部蓋牌呈現；身份公開的規則此欄位為 `false`，維持兩端蓋牌、中間兩張攤牌的
 * 傳統呈現方式。其餘副露種類固定為 `false`。
 */
data class MahjongMeldTileGroup(
    val type: MeldType,
    val tileIds: List<Uuid>,
    val calledTileId: Uuid?,
    val sourceDirection: RelativeDirection,
    val allTilesFaceDown: Boolean,
)

/**
 * 已由伺服器決定的單一玩家「桌角區域」呈現資料——手牌、摸牌位、副露三者合併成一次呼叫，理由見
 * [MahjongPlayerAreaPresenter] KDoc。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatIndex 這位玩家在 `TableState.players` 的固定座位 index。
 * @property standingTileIds 這位玩家目前立牌張數，依加入手牌的時間軸排列（`Hand.tiles`，不含
 * [drawnTileId]；先加入的在前，例如非摸切捨牌時併入立牌的 `Hand.lastDrawn` 排在最後面），鍵為
 * [IdentifiedTile.id]；空清單代表這局結束，只需要清除舊牌。這個順序不是畫面上的左右順序——實作端會
 * 自行反過來對應到左右座標，讓最後加入的那張牌落在玩家自己右手邊，符合真實麻將摸牌後插入手牌的直覺
 * 方向，見 `FabricMahjongPlayerAreaPresenter.present` KDoc。
 * @property drawnTileId 這位玩家目前摸到、尚未併入立牌或打出的那張牌 Uuid（`Hand.lastDrawn`）；
 * `null` 代表目前沒有摸牌位要呈現。
 * @property melds 這位玩家目前所有副露，依宣告順序排列——第一組（最早宣告）位於副露區固定的桌角
 * 錨點外緣（積棒外緣，見 [comboStickCount]），後續每組依序往玩家自己手牌方向排開，呼叫端不需要
 * 另外傳遞位置索引。
 * @property comboStickCount 這位玩家目前該顯示的積棒（連莊棒）支數——只有莊家非零，等於
 * `TableState.comboCount`；只用來讓手牌／副露正確讓開積棒佔用的空間，這個 presenter 本身不負責
 * 積棒 entity 的生成／清除，見 [MahjongScoringStickPresenter]。
 * @property animateDrawnTile [drawnTileId] 非 `null` 時，是否要播放摸牌動畫（牌從牌牆原位面朝下起飛、
 * 短暫隱形傳送到摸牌位、傳送的同一瞬間切換成面向玩家的姿態、解除隱形後再落下）——姿態切換發生在
 * 隱形期間，玩家看不到旋轉過程，不像開局發牌動畫那樣需要落地後另外播放看得見的翻牌動畫。只有真正的
 * 摸牌事件該傳 `true`，其餘呼叫端維持預設 `false` 直接定格顯示，不重複播放動畫。
 */
data class MahjongPlayerAreaPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val drawnTileId: Uuid?,
    val melds: List<MahjongMeldTileGroup>,
    val comboStickCount: Int,
    val animateDrawnTile: Boolean = false,
)

/**
 * 已由伺服器決定的開局發牌動畫呈現資料——只涵蓋立牌本身，不含摸牌位／副露／積棒：開局當下沒有人已經
 * 摸牌、也沒有任何副露，理由同 [MahjongPlayerAreaPresentation.comboStickCount] 只在有副露/積棒時才有
 * 意義。
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
 * @property dealerSeatIndex 本局莊家座位 index，只用來換算積棒佔用寬度（[comboStickCount] 只有莊家
 * 非零），跟牌牆的莊家相對旋轉無關——理由同 [MahjongPlayerAreaPresentation]，手牌位置不需要莊家相對
 * 旋轉。
 * @property comboStickCount 開局當下該顯示的積棒（連莊棒）支數，只用來讓手牌正確讓開積棒佔用的空間，
 * 理由同 [MahjongPlayerAreaPresentation.comboStickCount]。
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
    val comboStickCount: Int,
    val dealBatchSizes: List<Int>,
    val extraLeadDelayTicks: Int = 0,
)

/** 正式桌角區域呈現請求的處理結果。 */
enum class MahjongPlayerAreaPresentationResult {
    /** 已把這位玩家的手牌／摸牌位／副露更新為本次要呈現的狀態。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /**
     * 其中一張以上的牌找不到對應的既有 entity 可以領走——手牌／副露的牌應該早在牌牆生成時就已經
     * 存在（同一個 UUID），這裡不重新建立新 entity，只做「找到、改標記、移動」；找不到通常代表遊戲
     * 狀態跟世界狀態已經不一致。找不到的牌會被跳過，其餘牌仍照常呈現，不是整批回滾。
     */
    SPAWN_FAILED,
}

/**
 * 將權威手牌／摸牌位／副露呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 三者原本各自獨立成 `MahjongHandTilesPresenter`／`MahjongMeldPresenter`，但手牌／摸牌位要能對副露
 * （以及積棒）讓開空間，前提是同一次計算必須同時知道「這一側總共被佔用多少寬度」——手牌被觸發呈現
 * 的當下，如果不知道目前副露佔了多寬，就沒辦法算出正確的讓開偏移；反之亦然。這幾個東西必須包裝成
 * 同一個方法呼叫，才有辦法完整地透過計算算出退讓的偏移，因此合併成這一個 presenter，每次呼叫都帶齊
 * 立牌、摸牌、副露三者目前的完整狀態。
 *
 * 三者裡每一張牌的 UUID 都跟牌牆結構座標裡的同一張牌完全相同——這副牌本來就是從牌牆摸出來分給
 * 玩家、或由玩家鳴牌鳴來的，不是另外複製出一批新牌。實作因此不建立新 entity，而是找到
 * [MahjongTileWallPresenter] 已經生成好的既有 entity，直接改標記、改姿態、移動位置，比照
 * [MahjongTileWallPresenter] 的 best-effort 慣例。
 *
 * 積棒（[MahjongPlayerAreaPresentation.comboStickCount]）本身的 entity 生成／清除**不**歸這個
 * presenter 管——積棒數量整局固定、跟牌牆同時生成，生命週期完全不同步於手牌／副露（每次打牌/摸牌/
 * 鳴牌都會觸發），交給獨立的 [MahjongScoringStickPresenter]；這裡只把 `comboStickCount` 當成算讓開
 * 寬度用的數字。
 *
 * [MahjongTileTableLayout.meldPlacement]／[MahjongTileTableLayout.handPlacement] 只負責單一格位／
 * 單張牌座標，把多組副露、各組張數、各組內鳴取牌位置、積棒佔用寬度換算成這些函式需要的參數是實作的
 * 責任——見 [MahjongTileTableLayout.meldAreaWidth]／[MahjongTileTableLayout.stickAreaWidth]／
 * [MahjongTileTableLayout.handCornerYieldShift] 的 KDoc。
 */
interface MahjongPlayerAreaPresenter {
    /**
     * 在指定桌面呈現這位玩家的手牌／摸牌位／副露；[MahjongPlayerAreaPresentation.standingTileIds] 與
     * [MahjongPlayerAreaPresentation.melds] 皆為空、[MahjongPlayerAreaPresentation.drawnTileId] 為
     * `null` 時等同只清除舊牌。
     */
    fun present(presentation: MahjongPlayerAreaPresentation): MahjongPlayerAreaPresentationResult

    /**
     * 呈現開局發牌動畫：依 [MahjongInitialDealPresentation.dealBatchSizes] 分批，每批同時對所有座位
     * 執行「牌從牌山原位小幅起飛→頂點瞬間重新排列到手牌列上空、面朝下→落下到最終手牌位置」的動畫，
     * 播完後牌維持面朝下姿態靜置在最終手牌位置——翻牌動畫是後續獨立的一步，不在這個方法的職責內。
     *
     * 跟 [present] 一樣不建立新 entity，領走 [MahjongTileWallPresenter] 已生成好的既有牌牆 entity。
     */
    fun presentInitialDeal(presentation: MahjongInitialDealPresentation): MahjongPlayerAreaPresentationResult

    /** 清除指定桌子目前的正式手牌／摸牌位／副露用牌；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
