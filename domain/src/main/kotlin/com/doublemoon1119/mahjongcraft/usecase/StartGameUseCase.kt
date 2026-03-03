package com.doublemoon1119.mahjongcraft.usecase

import com.doublemoon1119.mahjongcraft.model.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.model.table.TableState
import com.doublemoon1119.mahjongcraft.model.table.Wind
import com.doublemoon1119.mahjongcraft.usecase.factory.MahjongModuleRegistry
import java.util.*

/**
 * 開始遊戲的使用案例請求資料包。
 *
 * @property playerNames 玩家唯一識別碼與名稱之鍵值對。
 * @property config 本次對局套用之規則配置。
 */
data class StartGameRequest(
    val playerNames: Map<UUID, String>,
    val config: MahjongRuleConfig
)

/**
 * 開始遊戲的使用案例。
 *
 * 負責執行對局初始化的完整流程：
 * 1. 根據規則從註冊表獲取正確的組件模組。
 * 2. 建立牌山並執行洗牌動作。
 * 3. 初始化玩家及其對應規則之專屬牌河。
 * 4. 根據規則定義之初始張數發放手牌。
 *
 * @property registry 規則模組註冊表，用於跨規則檢索對應的組件工廠。
 */
class StartGameUseCase(
    private val registry: MahjongModuleRegistry
) {

    /**
     * 執行開始遊戲之業務邏輯。
     *
     * @param request 包含參與玩家資訊與規則配置的請求物件。
     * @return 包含初始化資料的 [TableState] 實體。
     * @throws IllegalStateException 當牌山剩餘數量不足以完成發牌時拋出。
     */
    operator fun invoke(request: StartGameRequest): TableState {
        // 1. 獲取規則對應的模組實作
        val module = registry.getModule(request.config)

        // 2. 準備牌山
        val wallFactory = module.createWallFactory(request.config)
        val tileWall = wallFactory.create()
        tileWall.shuffle()

        // 3. 初始化玩家並分配初始方位與點數
        val players = request.playerNames.entries.mapIndexed { index, (uuid, name) ->
            MahjongPlayer(
                id = uuid,
                name = name,
                initialSeat = Wind.entries.getOrElse(index) { Wind.EAST },
                discardPile = module.createDiscardPile(request.config)
            ).apply {
                score = request.config.scoreConfig.initialScore
            }
        }

        // 4. 初始化桌況狀態
        val tableState = TableState(
            players = players,
            tileWall = tileWall,
            config = request.config,
            prevalentWind = Wind.EAST,
            roundNumber = 1,
            currentPlayerIndex = 0
        )

        // 5. 執行發牌邏輯
        dealInitialHands(tableState)

        return tableState
    }

    /**
     * 從牌山中抽取牌發送給每位玩家。
     *
     * 若在發牌過程中牌山已空，將拋出異常以反映規則配置與實際牌組不符。
     *
     * @param tableState 待處理之桌況狀態。
     */
    private fun dealInitialHands(tableState: TableState) {
        val initialCount = tableState.config.initialHandSize
        tableState.players.forEach { player ->
            repeat(initialCount) {
                // 執行發牌時，若 draw() 返回 null 則視為嚴重錯誤
                val tile = tableState.tileWall.draw()
                    ?: throw IllegalStateException("Tile wall exhausted during initial dealing.")
                player.hand.addTile(tile)
            }
        }
    }
}
