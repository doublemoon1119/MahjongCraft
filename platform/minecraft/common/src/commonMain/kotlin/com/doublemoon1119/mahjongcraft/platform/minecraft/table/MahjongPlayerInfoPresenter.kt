package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.uuid.Uuid

/** 單一固定座位的完整公開顯示快照。 */
data class MahjongPlayerInfoEntry(
    val playerId: Uuid,
    val playerName: String,
    val isAi: Boolean,
    val seatIndex: Int,
    val seatWind: Wind,
    val score: Int,
    val indicators: List<PublicPlayerIndicator>,
)

/** 一桌一個 entity 所需的規則公開資料，不含世界位置與方塊朝向。 */
data class MahjongPlayerInfoPresentation(
    val tableId: Uuid,
    val dealerPlayerId: Uuid,
    val players: List<MahjongPlayerInfoEntry>,
)

/** 集中建立玩家公開快照，避免 use case 或 loader adapter 各自挑選規則資料。 */
object MahjongPlayerInfoPresentationFactory {
    fun create(
        tableState: TableState,
        module: MahjongRuleModule<*>,
        resolvePlayerName: (MahjongPlayer) -> String,
    ): MahjongPlayerInfoPresentation = MahjongPlayerInfoPresentation(
        tableId = tableState.id,
        dealerPlayerId = tableState.dealerPlayerId,
        players = tableState.players.mapIndexed { seatIndex, player ->
            MahjongPlayerInfoEntry(
                playerId = player.id,
                playerName = resolvePlayerName(player),
                isAi = player.isAi,
                seatIndex = seatIndex,
                seatWind = player.seatWind,
                score = player.score,
                indicators = module.getPublicPlayerIndicators(tableState, player),
            )
        },
    )
}

/** 玩家公開資訊 entity 的建立／更新結果。 */
enum class MahjongPlayerInfoPresentationResult { PRESENTED, TABLE_NOT_FOUND, SPAWN_FAILED }

/** Minecraft loader 實作的一桌一 entity 玩家資訊邊界。 */
interface MahjongPlayerInfoPresenter {
    fun present(
        presentation: MahjongPlayerInfoPresentation,
        tableLocation: TableLocation,
        tableFacing: MahjongTableFacing,
    ): MahjongPlayerInfoPresentationResult

    /** 隱藏期限只能延長；找不到 entity 時安全忽略。 */
    fun hideUntil(tableId: Uuid, tableLocation: TableLocation, gameTime: Long)

    /** 清除指定桌子的玩家公開資訊，回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
