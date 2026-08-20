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
 * 已由伺服器決定的單一玩家副露呈現資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatIndex 副露所屬玩家在 `TableState.players` 的固定座位 index。
 * @property melds 這位玩家目前所有副露，依宣告順序排列——順序本身決定副露區左到右的排列位置，不需要
 * 另外傳遞位置索引；空清單代表目前沒有任何副露，只需要清除舊牌。
 */
data class MahjongMeldPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatIndex: Int,
    val melds: List<MahjongMeldTileGroup>,
)

/** 正式副露呈現請求的處理結果。 */
enum class MahjongMeldPresentationResult {
    /** 已把這位玩家的副露更新為本次要呈現的牌（或 [MahjongMeldPresentation.melds] 為空、只清除舊牌）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /**
     * 其中一張以上的牌找不到對應的既有 entity 可以領走——副露的牌應該早在牌牆生成時就已經存在
     * （同一個 UUID），這裡不重新建立新 entity，只做「找到、改標記、移動」；找不到通常代表遊戲
     * 狀態跟世界狀態已經不一致。找不到的牌會被跳過，其餘牌仍照常呈現，不是整批回滾。
     */
    SPAWN_FAILED,
}

/**
 * 將權威副露呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供依宣告順序排列的副露列表；entity 查找、姿態設定、移動位置（含鳴取牌側身）均由實作處理。
 * 比照 [MahjongDiscardPresenter] 的 best-effort 慣例，副露是獨立於手牌／牌河的呈現區域，因此獨立成
 * 一組 presenter，不塞進 [MahjongHandTilesPresenter]。
 *
 * [MahjongTileTableLayout.meldPlacement] 只負責單一格位座標，把多組副露、各組張數、各組內鳴取牌位置
 * 換算成該函式需要的桌角錨點偏移量是實作的責任——見該函式 KDoc。
 */
interface MahjongMeldPresenter {
    /** 在指定桌面呈現這位玩家的副露；[MahjongMeldPresentation.melds] 為空時等同只清除舊牌。 */
    fun present(presentation: MahjongMeldPresentation): MahjongMeldPresentationResult

    /** 清除指定桌子目前的正式副露用牌；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
