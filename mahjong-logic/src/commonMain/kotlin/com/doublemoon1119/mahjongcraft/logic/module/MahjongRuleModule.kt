package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpeningPolicy
import com.doublemoon1119.mahjongcraft.logic.tile.IdentityTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import kotlin.uuid.Uuid

/**
 * 麻將規則模組介面。
 *
 * 本介面定義了特定麻將規則（如日麻、台麻）必須提供的核心組件。
 * UseCase 層透過此介面獲取具體組件，而不直接依賴特定的規則實作。
 *
 * @param T 該模組所支援的規則配置型別，必須繼承自 [MahjongRuleConfig]。
 */
interface MahjongRuleModule<T : MahjongRuleConfig> {
    /**
     * 規則模組的唯一識別碼。
     * 此 ID 由註冊中心於實例化時注入，代表該模組在系統中的身份。
     */
    val id: String

    /**
     * 該模組實例所持有的規則配置。
     */
    val config: T

    /**
     * 該規則整理手牌（[Hand.organize]）時使用的排序規則。
     */
    val tileOrder: TileOrder

    /**
     * 建立適用於該規則的牌山生成工廠。
     *
     * @return 實作了 [TileWallFactory] 的工廠物件。
     */
    fun createWallFactory(): TileWallFactory

    /**
     * 建立適用於該規則的牌牆開門 policy。
     *
     * 尚未定義骰子開門規則的模組可使用預設的 null；通用初始化流程不得自行套用其他玩法的公式。
     *
     * @return 此規則的 [WallOpeningPolicy]，若尚未支援權威開門流程則為 null。
     */
    fun createWallOpeningPolicy(): WallOpeningPolicy? = null

    /**
     * 建立適用於該規則的牌牆布局能力，將洗牌後的完整牌組依開門結果排列成正式的摸牌順序與結構。
     *
     * 尚未定義牌牆布局的模組可使用預設的 null；通用初始化流程不得自行假設固定張數或固定每面墩數。
     *
     * @return 此規則的 [TileWallLayout]，若尚未支援則為 null。
     */
    fun createWallLayout(): TileWallLayout? = null

    /**
     * 建立目前規則用於一般牌面比較的解讀 policy。
     *
     * 沒有規則特有等價牌面的模組可使用預設原樣實作；共用流程不得自行判斷特定 extension ID。
     */
    fun createTileInterpretationPolicy(): TileInterpretationPolicy = IdentityTileInterpretationPolicy

    /**
     * 建立適用於該規則的捨牌堆（牌河）實作。
     *
     * @return 實作了 [DiscardPile] 的物件。
     */
    fun createDiscardPile(): DiscardPile<*>

    /**
     * 建立適用於該規則的向聽數計算器 (Shanten Calculator)。
     *
     * 負責分析手牌距離聽牌狀態的最小進張數。
     *
     * @return 實作了 [ShantenCalculator] 的物件。
     */
    fun createShantenCalculator(): ShantenCalculator

    /**
     * 建立適用於該規則的合法動作判定器 (Legal Action Validator)。
     *
     * 負責根據當前遊戲狀態判斷玩家可以執行的所有合法動作。
     *
     * @return 實作了 [LegalActionValidator] 的物件。
     */
    fun createLegalActionValidator(): LegalActionValidator

    /**
     * 建立適用於該規則的手牌役種計算機 (Hand Value Calculator)。
     *
     * 負責計算手牌的役種、番數（或台數），用於胡牌結算與役種顯示。
     *
     * @return 實作了 [HandValueCalculator] 的物件。
     */
    fun createHandValueCalculator(): HandValueCalculator<*, *>

    /**
     * 建立適用於該規則的手牌役種上下文計算機 (Hand Value Context Calculator)。
     *
     * 負責根據當前遊戲狀態計算役種結算所需的上下文資訊，
     * 例如：寶牌指示牌、裏寶牌、海底撈月、河底撈魚等。
     *
     * @return 實作了 [HandValueContextCalculator] 的物件。
     */
    fun createHandValueContextCalculator(): HandValueContextCalculator<*, *>

    /**
     * 建立該規則的初始動態桌況狀態。
     *
     * 由 [GameInitializer] 在開局時寫入 `TableState.dynamicRuleState`。
     * 沒有動態狀態需求的規則可回傳 null。
     *
     * @return 該規則的初始 [DynamicRuleState]，若無則為 null。
     */
    fun createInitialDynamicState(): DynamicRuleState?

    /**
     * 建立該規則的初始玩家規則狀態。
     *
     * 由 [GameInitializer] 在開局時寫入每位 `MahjongPlayer.playerRuleState`。
     * 沒有玩家規則狀態需求的規則可回傳 null。
     *
     * @return 該規則的初始 [PlayerRuleState]，若無則為 null。
     */
    fun createInitialPlayerRuleState(): PlayerRuleState?

    /**
     * 套用一次立直宣告（[GameAction.Riichi]）的規則特有狀態變化：把捨牌紀錄標記為立直宣告牌、
     * 更新玩家的規則狀態（立直/雙立直/一發資格）、更新桌況的動態規則狀態（如立直棒數量）。
     *
     * 呼叫前應已確認 [GameAction.Riichi] 目前合法（例如透過 [createLegalActionValidator]），
     * 且 [discardResult] 對應的捨牌後手牌仍聽牌——這兩者是規則無關的驗證，由呼叫端負責；
     * 這裡只處理套用規則特有狀態的部分，因此呼叫端（如 `:mahjong-flow` 的 use case）永遠
     * 不需要知道、也不需要轉型成任何規則專屬的具體型別。
     *
     * 不支援立直宣告的規則應回傳 null。
     *
     * @param tableState 目前的桌況（尚未套用本次宣告）。
     * @param player 宣告立直的玩家（尚未套用本次宣告）。
     * @param discardResult 打出宣告牌的捨牌結果。
     * @return 套用宣告後的新玩家實例與新的動態規則狀態，若此規則不支援立直宣告則為 null。
     */
    fun declareRiichi(
        tableState: TableState,
        player: MahjongPlayer,
        discardResult: Hand.DiscardResult,
    ): RiichiDeclarationResult?

    /**
     * 因應「玩家摸牌」事件，套用規則特有的狀態清除。
     *
     * 例如日麻：摸牌代表一發窗口已經結束（本巡未能胡牌），需清除玩家的一發資格。
     * 沒有對應狀態需求的規則應直接回傳 [player] 本身，不做任何事。
     *
     * @param player 剛完成摸牌的玩家（已套用摸牌本身造成的變化）。
     * @return 套用規則特有狀態清除後的新玩家實例。
     */
    fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer

    /**
     * 因應「有玩家完成一次鳴牌（吃/碰/槓）」事件，套用規則特有的狀態清除。
     *
     * 例如日麻：任何一次鳴牌都會讓場上所有玩家的一發資格失效（不只鳴牌的當事人）。
     * 沒有對應狀態需求的規則應直接回傳 [players] 本身，不做任何事。
     *
     * @param players 鳴牌動作套用之後的完整玩家列表。
     * @return 套用規則特有狀態清除後的新玩家列表。
     */
    fun onMeldClaimed(players: List<MahjongPlayer>): List<MahjongPlayer>

    /**
     * 檢查本次碰／明槓是否觸發包牌責任，若觸發則寫入 [claimingPlayer] 的規則狀態。
     *
     * 必須在鳴牌動作實際套用到 [claimingPlayer] 手牌「之前」呼叫，以取得鳴牌當下、
     * 尚未加入新副露的手牌狀態。不支援包牌概念的規則應直接回傳
     * [claimingPlayer] 本身，不做任何事。
     *
     * @param claimingPlayer 執行碰／明槓的玩家（尚未套用本次鳴牌）。
     * @param calledTile 本次鳴取的他家捨牌。
     * @param sourceDirection 本次鳴取的來源相對方位。
     * @return 套用包牌責任（若觸發）後的新玩家實例。
     */
    fun applyPaoLiabilityIfTriggered(
        claimingPlayer: MahjongPlayer,
        calledTile: IdentifiedTile,
        sourceDirection: RelativeDirection,
    ): MahjongPlayer

    /**
     * 計算一次自摸胡牌（[GameAction.Tsumo]）的點數結算：贏家實際獲得的點數，以及各應付款玩家
     * 應支付的金額（依莊家/閒家身分、包牌責任等區分）。
     *
     * 呼叫前應已確認 [GameAction.Tsumo] 目前合法（例如透過 [createLegalActionValidator]）——這是
     * 規則無關的驗證，由呼叫端負責；這裡只處理規則特有的點數計算與分攤方式，因此呼叫端（如
     * `:mahjong-flow` 的 use case）永遠不需要知道、也不需要轉型成任何規則專屬的具體型別。
     *
     * [player] 應為胡牌當下、尚未套用本次自摸任何變化的玩家實例（即 [tableState] 中對應的
     * `TableState.currentPlayer`），其 `Hand.lastDrawn` 即為胡牌張。實作內部需自行處理「胡牌張
     * 同時存在於 `hand.lastDrawn`、又要餵給役種計算所需的胡牌張參數」這個潛在的重複計數問題，
     * 呼叫端不需要、也不應該自行處理這個細節。
     *
     * 不支援自摸結算的規則應回傳 null。
     *
     * @param tableState 目前的桌況（尚未套用本次自摸結算）。
     * @param player 宣告自摸的玩家（尚未套用本次自摸結算），其 `hand.lastDrawn` 即為胡牌張。
     * @return 本次自摸的點數結算結果，若此規則不支援自摸結算、或 [player] 尚未摸牌則為 null。
     */
    fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinSettlementResult?

    /**
     * 計算一次榮和胡牌（[GameAction.Ron]）的點數結算：贏家實際獲得的點數，以及各應付款玩家
     * 應支付的金額（一般情況下由放銃者一人支付全額；包牌責任成立時由包牌責任者與放銃者平分，
     * 但若包牌責任者恰好就是放銃者本人，視為單一玩家支付全額）。
     *
     * 呼叫前應已確認 [GameAction.Ron] 目前合法（例如透過 [createLegalActionValidator]）——這是
     * 規則無關的驗證，由呼叫端負責；這裡只處理規則特有的點數計算與分攤方式，因此呼叫端（如
     * `:mahjong-flow` 的 use case）永遠不需要知道、也不需要轉型成任何規則專屬的具體型別。
     *
     * [player] 應為胡牌當下、尚未套用本次榮和任何變化的玩家實例，[winningTile] 為放銃者打出、
     * 被榮和的那張牌。[RiichiPointResult] 這類規則特有的點數結果本身不帶玩家身分，因此需要呼叫端
     * 額外透過 [discarderId] 告知放銃者是誰，實作才能把付款金額正確歸屬到實際玩家。
     *
     * [isRobbingKan] 為 `true` 時代表這次榮和是搶槓（[GameAction.KanType.ADDED_KAN] 被搶），
     * 支援搶槓役種加成的規則應在此時額外計入（例如日麻的搶槓 1 翻）；預設 `false`，一般捨牌榮和
     * 不需要呼叫端額外傳入。
     *
     * 不支援榮和結算的規則應回傳 null。
     *
     * @param tableState 目前的桌況（尚未套用本次榮和結算）。
     * @param player 宣告榮和的玩家（尚未套用本次榮和結算）。
     * @param winningTile 被榮和的捨牌。
     * @param discarderId 打出 [winningTile] 的玩家 Uuid。
     * @param isRobbingKan 這次榮和是否為搶槓（搶加槓）。
     * @return 本次榮和的點數結算結果，若此規則不支援榮和結算則為 null。
     */
    fun declareRon(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
        discarderId: Uuid,
        isRobbingKan: Boolean = false,
    ): WinSettlementResult?

    /**
     * 胡牌時，贏家收下場上供託（如立直棒）所增加的點數，以及收下後應套用的新動態桌況狀態。
     *
     * 不區分自摸／榮和／多家和——呼叫端決定「這次由誰收下」（自摸與單一贏家榮和是唯一贏家；
     * 多家和則依頭跳順位由離放銃者最近的贏家收下，見 [TableState.nearestPlayerInTurnOrder]），
     * 這裡只負責算出「收下後供託剩多少、贏家因此多拿了多少點數」。
     *
     * 不支援供託機制的規則應回傳 null；即使場上目前沒有供託可收（例如立直棒數量為 0），
     * 只要規則本身有這個機制就應回傳非 null（金額為 0、狀態不變）——null 專門用來表示
     * 「這個規則根本沒有供託機制」，不跟「目前沒有供託」混用。
     *
     * @param tableState 目前的桌況（尚未套用本次收供託的變化）。
     * @return 收下供託後應套用的動態桌況狀態，以及贏家因此獲得的點數；若此規則沒有供託機制則為 null。
     */
    fun collectStickPot(tableState: TableState): Pair<DynamicRuleState?, Int>?

    /**
     * 計算一次一般流局（牌山摸盡）的點數結算：聽牌/不聽罰符的拆分，以及流局滿貫成立時
     * 視為自摸滿貫的點數結算（兩者互斥，流局滿貫成立時不再進行不聽罰符收授）。
     *
     * 不支援一般流局結算的規則應回傳 null。
     *
     * @param tableState 目前的桌況（尚未套用本次流局結算，牌山應已摸盡）。
     * @return 本次一般流局的結算結果，若此規則不支援則為 null。
     */
    fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult?

    /**
     * 多家和依 [MahjongRuleConfig.multiRonPolicy] 判定為流局（[RonResolution.ABORTIVE_DRAW]）時，
     * 該規則對應的具體流局原因。
     *
     * 不支援此流局類型的規則應回傳 null。
     *
     * @return 該規則對應的流局原因，若此規則不支援則為 null。
     */
    fun resolveMultiRonAbortiveDraw(): ExhaustiveDrawReason?

    /**
     * 這次捨牌後，是否構成四風連打（第一巡、全員的第一張捨牌皆為同一種風牌，且都沒人反應）。
     *
     * 只應在確定這次捨牌沒有任何人可以吃/碰/槓/榮和之後才呼叫。
     *
     * @param tableStateAfterDiscard 捨牌且確定無人反應後的桌況。
     * @return 若構成四風連打則為對應的流局原因，否則為 null；不支援此流局類型的規則固定回傳 null。
     */
    fun resolveSuufonRenda(tableStateAfterDiscard: TableState): ExhaustiveDrawReason?

    /**
     * 這次立直宣告後，是否構成四家立直（全員皆已宣告立直，且這張立直宣告牌沒人反應）。
     *
     * 只應在「剛套用完一次立直宣告」且確定這張宣告牌沒有任何人可以吃/碰/槓/榮和之後才呼叫——
     * 只有立直宣告的呼叫端會呼叫這個方法，一般捨牌不會（否則同一副立直保持到底的牌局，往後每次
     * 捨牌都會被誤判成四家立直）。
     *
     * @param tableStateAfterDeclaration 立直宣告且確定無人反應後的桌況。
     * @return 若構成四家立直則為對應的流局原因，否則為 null；不支援此流局類型的規則固定回傳 null。
     */
    fun resolveSuuchaRiichi(tableStateAfterDeclaration: TableState): ExhaustiveDrawReason?

    /**
     * 全場玩家合計是否已槓了 4 次（明槓、暗槓、加槓皆算），且並非全部由同一人達成
     * （若全部由同一人達成，該玩家可能正在做「四槓子」役滿，不觸發流局）。
     *
     * 只應在確定某次槓牌的嶺上摸牌已經處理完畢（例如玩家已經有機會嘗試嶺上開花自摸）之後才呼叫。
     *
     * @param tableState 目前的桌況。
     * @return 若構成四槓散了則為對應的流局原因，否則為 null；不支援此流局類型的規則固定回傳 null。
     */
    fun resolveSuukanNagare(tableState: TableState): ExhaustiveDrawReason?

    /**
     * 除了 [MahjongRuleConfig.gameLength]（`totalRounds`）之外，該規則是否有額外造成整場對局立即
     * 結束的條件（例如日麻的擊飛：任一玩家分數低於 0）。每次連莊/過莊判定完成之後呼叫一次，讓規則
     * 模組能疊加使對局提前結束的條件——只會、也只能讓對局比 `totalRounds` 更早結束，不能用來延後
     * 結束（例如日麻的烏本延長賽需要「打完最後一局仍沒人達標時延長到西入」，那是完全不同的機制，
     * 需要動態調整 `totalRounds` 本身，不是這裡能表達的；此 hook 尚未支援這類延長情境）。
     *
     * 不支援額外結束條件的規則應直接回傳 `false`，不代表「這個規則沒有結束條件」，只代表這個規則
     * 沒有比 `totalRounds` 更早結束對局的額外條件。
     *
     * @param tableState 目前桌況——呼叫時機在本局點數結算之後、`TableState.advanceRound` 判定完成
     * 之後，分數已經是本局結算完畢後的最終值。
     * @return 是否應該立即結束整場對局。
     */
    fun hasAdditionalMatchEndCondition(tableState: TableState): Boolean

    /**
     * 給定一張牌與目前已公開翻開的牌山牌張（[revealedWallTiles]，一般是各規則自訂的指示牌），判斷這張
     * 牌目前是否該有特殊視覺強調（例如日麻的寶牌發光）——純粹牌面比對，不需要完整 [TableState]，
     * client／server 都能呼叫。呈現層只負責「有沒有」，實際疊加什麼視覺效果由呈現層自己決定，這裡
     * 刻意不用任何特定規則的術語命名，避免介面綁死成只有日麻會用。
     *
     * 不支援此概念的規則固定回傳 `false`（例如台麻沒有寶牌）。
     *
     * @param tile 欲判斷的牌面。
     * @param revealedWallTiles 目前已公開翻開的牌山牌張列表。
     * @return 這張牌目前是否該有特殊視覺強調。
     */
    fun isHighlightedTile(tile: Tile, revealedWallTiles: List<Tile>): Boolean = false

    /**
     * 這位玩家目前算不算「立直中」——只有支援立直的規則（日麻）需要覆寫，用來讓呈現層知道該不該在
     * 這個座位面前顯示立直棒，刻意不用 [PlayerRuleState] 以外任何規則專屬的具體型別命名，呼叫端也不
     * 該自行轉型成特定規則的 [PlayerRuleState] 實作（例如 `RiichiPlayerState`）來回答這個問題——理由
     * 同 `DeclareRiichiUseCase` KDoc「刻意不轉型成任何規則專屬的具體型別」的說明。
     *
     * 不支援立直的規則（例如台麻）固定回傳 `false`。
     *
     * @param player 欲判斷的玩家。
     * @return 這位玩家目前是否算立直中。
     */
    fun isPlayerInRiichi(player: MahjongPlayer): Boolean = false

    /**
     * 場上目前尚未被任何人收下的供託數量——純查詢，不像 [collectStickPot] 會連帶把狀態歸零，用來讓
     * 呈現層在收下之前（例如流局延續到下一局、或宣告供託當下）也能知道場上目前累積多少供託。是供託
     * 本身的數量（例如日麻立直棒的支數），不是換算後的點數——換算成點數是 [collectStickPot] 的職責。
     *
     * 不支援供託機制的規則維持預設值 `0`。
     *
     * @param tableState 目前的桌況。
     * @return 場上目前尚未被收下的供託數量。
     */
    fun getStickPotCount(tableState: TableState): Int = 0

    /**
     * 桌面局況顯示的規則自訂延伸項目（例如日麻的立直棒累積支數）——不強迫每種規則的專屬資訊都擠進
     * 呈現層固定欄位，呈現層看到不認得的 [RoundInfoExtra.key] 時應該略過該行，不是報錯。
     *
     * 不支援延伸顯示的規則維持預設空清單。
     *
     * @param tableState 目前的桌況。
     * @return 這個規則想額外顯示的局況項目列表。
     */
    fun getRoundInfoExtras(tableState: TableState): List<RoundInfoExtra> = emptyList()

    /**
     * 因應「玩家放棄一次原本合法的和牌機會」事件，套用規則特有的狀態變化——例如日麻立直後放過榮和
     * （他家打出你的和牌張、你選擇過）或摸切棄胡（自己摸到和牌張卻選擇打出），從此本局起永久振聽，
     * 不論之後 `passedTilesInRound` 是否因為摸牌而清空都不能再榮和，只能自摸；未立直時放過和牌
     * 只構成一般的同巡振聽，會隨下一次摸牌清除，不需要呼叫這個 hook。
     *
     * 沒有對應規則需求的規則應直接回傳 [player] 本身，不做任何事。
     *
     * @param player 剛放棄和牌機會的玩家。
     * @return 套用規則特有狀態變化後的新玩家實例。
     */
    fun onPlayerDeclinedWin(player: MahjongPlayer): MahjongPlayer = player
}
