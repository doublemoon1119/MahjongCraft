package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableController
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.AutomaticControlUpdateService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryManagementResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.koin.core.Koin
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 伺服器自動測試的一張牌桌：一種規則、一位機器人房主，以及照玩家流程進行的開房、加入 AI、開局與開自動操作。
 *
 * @property rule 規則模組識別碼。
 * @property botName 這張牌桌房主的機器人名稱。
 * @property position 牌桌控制方塊的位置。
 */
internal class AutotestServerTable(val rule: String, val botName: String, val position: BlockPos) {
    /** 牌桌識別碼，放好牌桌之前為 null。 */
    var tableId: Uuid? = null

    /** 機器人站在牌桌前方的位置。不在任何一張測試牌桌的範圍內，所以機器人不會擋住放牌桌。 */
    val standingPosition: Vec3
        get() = Vec3(position.x + 0.5, position.y.toDouble(), position.z - 2.5)

    /**
     * 讓機器人走到牌桌前方面向牌桌。
     *
     * @param bot 機器人玩家。
     */
    fun approach(bot: ServerPlayer) {
        bot.teleportTo(bot.serverLevel(), standingPosition.x, standingPosition.y, standingPosition.z, 0f, 30f)
    }

    /**
     * 與玩家相同地手持麻將桌對地面使用，放下牌桌並記下牌桌識別碼。
     *
     * @param bot 機器人玩家。
     * @return 放好時的牌桌識別碼。沒有放好時為 null。
     */
    fun place(bot: ServerPlayer): Uuid? {
        approach(bot)
        val level = bot.serverLevel()
        val stack = ItemStack(BuiltInRegistries.ITEM.get(resourceId(MinecraftModMetadata.MOD_ID, TABLE_ITEM)))
        bot.setItemInHand(InteractionHand.MAIN_HAND, stack)
        val below = position.below()
        bot.gameMode.useItemOn(bot, level, stack, InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(below).add(0.0, 0.5, 0.0), Direction.UP, below, false))
        bot.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        tableId = (level.getBlockEntity(position) as? MahjongTableController)?.mahjongTableState?.tableId
        return tableId
    }

    /**
     * 這張牌桌的房間設定：預設的流程設定加上這個規則的預設規則設定。
     *
     * @param koin 取得設定呈現定義。
     * @param current 房間目前的設定。
     * @return 這個規則的預設設定。
     */
    fun gameConfig(koin: Koin, current: GameConfig): GameConfig {
        val presentations = koin.get<GameConfigPresentationRegistry>()
        val ruleConfig = checkNotNull(presentations.find(rule)) { "No settings for rule $rule" }.defaultRuleConfig()
        return GameConfig(ruleConfig = ruleConfig, flowConfig = presentations.defaultFlowConfig(rule, ruleConfig, current.flowConfig))
    }

    /** 牌桌相關的服務與查詢。 */
    companion object {
        /** 放下的麻將桌物品。 */
        private const val TABLE_ITEM = "wooden_mahjong_table"

        /** 測試的三張牌桌：日麻、台麻與三人麻將，各有一位機器人房主。 */
        fun all(): List<AutotestServerTable> = listOf(
            AutotestServerTable(BuiltInRuleModuleIds.RIICHI, "MahjongBot1", BlockPos(0, -60, 6)),
            AutotestServerTable(BuiltInRuleModuleIds.TAIWAN, "MahjongBot2", BlockPos(12, -60, 6)),
            AutotestServerTable(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, "MahjongBot3", BlockPos(24, -60, 6)),
        )

        /**
         * 機器人玩家的識別碼。
         *
         * @receiver 機器人玩家。
         * @return 玩家識別碼。
         */
        fun ServerPlayer.playerId(): Uuid = uuid.toKotlinUuid()

        /**
         * 替機器人開啟自動操作：自動摸切、自動和牌、不吃碰槓，與玩家在畫面上開啟相同。
         *
         * @param koin 取得自動操作服務。
         * @param game 機器人所在的牌局。
         * @param bot 機器人玩家。
         * @return 伺服器接受時為 true。
         */
        suspend fun enableAutomaticControls(koin: Koin, game: Game, bot: ServerPlayer): Boolean {
            val result = koin.get<AutomaticControlUpdateService>().invoke(
                playerId = bot.playerId(),
                request = AutomaticControlUpdateRequestDto(
                    requestId = "autotest-${Uuid.random()}",
                    gameId = game.id.toString(),
                    expectedRevision = game.automaticControlRevision,
                    enabledControlIds = AUTOMATIC_CONTROLS,
                ),
            )
            return result != null && game.id.let { koin.get<GameRepository>().getGame(it) }?.enabledAutomaticControlIdsByPlayerId?.get(bot.playerId()) == AUTOMATIC_CONTROLS
        }

        /**
         * 這一場是否已經封存進牌譜。
         *
         * @param koin 取得牌譜寫入元件。
         * @param matchId 這一場的識別碼。
         * @return 已經封存時為 true。
         */
        suspend fun isArchived(koin: Koin, matchId: Uuid): Boolean {
            val writer = koin.get<HistoryOutboxWriter>()
            val sessionId = writer.currentSessionId ?: return false
            val receipt = writer.generationReceipt(setOf(matchId), sessionId) as? HistoryManagementResult.Success ?: return false
            return matchId.toString() in receipt.value.replayBytes
        }

        /**
         * 牌桌是否在等待房間中，也就是沒有進行中的牌局。
         *
         * @param koin 取得房間與牌局。
         * @param tableId 牌桌識別碼。
         * @return 房間存在而且沒有牌局時為 true。
         */
        suspend fun isWaitingRoom(koin: Koin, tableId: Uuid): Boolean = koin.get<RoomRepository>().getRoom(tableId) != null && koin.get<GameRepository>().getGame(tableId) == null

        /**
         * 牌局進度的摘要，重新讀回後應該完全相同。
         *
         * - 局位、莊家、輪到誰、牌山張數、捨牌順序。
         * - 各家的分數、捨牌張數與手牌。
         *
         * @param game 牌局。
         * @return 摘要文字。
         */
        fun fingerprint(game: Game): String {
            val table = game.tableState
            val hands = table.players.joinToString(";") { player ->
                val tiles = (player.hand.tiles.map { it.id.toString() }.sorted() + listOfNotNull(player.hand.lastDrawn?.id?.toString())).joinToString(",")
                "${player.id}:${player.score}:${player.discardPile.entries.size}:$tiles"
            }
            return "round=${table.roundSerial} position=${table.roundPosition} dealer=${table.dealerPlayerId} current=${table.currentPlayerIndex} " +
                "wall=${table.tileWall.getAllTiles().size} discards=${table.discardOrder.joinToString(",")} hands=$hands"
        }

        /** 替機器人開啟的自動操作。 */
        private val AUTOMATIC_CONTROLS = setOf("mahjongcraft:auto_tsumogiri", "mahjongcraft:auto_win", "mahjongcraft:decline_calls")

        /**
         * 房間的牌局設定是否已經是指定的設定。
         *
         * @param koin 取得房間。
         * @param tableId 牌桌識別碼。
         * @param config 指定的設定。
         * @return 相同時為 true。
         */
        suspend fun hasConfig(koin: Koin, tableId: Uuid, config: GameConfig): Boolean = koin.get<RoomRepository>().getRoom(tableId)?.gameConfig == config

        /**
         * 房間服務。
         *
         * @param koin 取得房間服務。
         * @return 房間服務。
         */
        fun rooms(koin: Koin): MahjongTableRoomService = koin.get()
    }
}
