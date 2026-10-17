package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.SnapshotClearedPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortEffects
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TableOpeningPresentationOperationTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTablePresentationCleaner
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.uuid.Uuid

/**
 * 清理 Fabric 對局中止後仍在執行的桌級呈現，並通知原本可見這場對局的玩家。
 *
 * 權威房間與對局已由 [GameAbortEffects] 的呼叫端處理；本類別只清理指定桌子的衍生呈現，
 * 不會刪除桌子、位置索引或其他桌子的工作。
 *
 * @property store 供主執行緒確認房間仍是同一份中止後狀態。
 * @property serverHolder 目前 Minecraft server 與啟動 session 的來源。
 * @property locations 桌子位置索引。
 * @property presentationCleaner 清除指定桌子的暫時性演出實體與慶祝特效。
 * @property busyTracker 清除指定桌子的待執行呈現 lease。
 * @property openingOperations 使指定桌子的舊開局呈現世代失效。
 * @property feedbackPublisher 發送本地化中止通知。
 * @property moduleRegistry 解析規則模組 ID，供診斷記錄使用。
 * @property dispatchers 伺服器主執行緒調度器。
 * @property json 既有快照失效通知使用的網路序列化設定。
 */
class FabricGameAbortEffects(
    private val store: AuthoritativeStateStore,
    private val serverHolder: FabricServerHolder,
    private val locations: TableLocationRegistry,
    private val presentationCleaner: FabricTablePresentationCleaner,
    private val busyTracker: TablePresentationBusyTracker,
    private val openingOperations: TableOpeningPresentationOperationTracker,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val moduleRegistry: MahjongModuleRegistry,
    private val dispatchers: CoroutineDispatchers,
    private val json: NetworkJson,
) : GameAbortEffects {
    /** 記錄中止後呈現清理的拒絕與結果。 */
    private val logger = mahjongCraftLogger(FabricGameAbortEffects::class)

    /** 在主執行緒確認中止狀態後，清除該桌舊呈現並通知原觀察者。 */
    override suspend fun apply(
        game: Game,
        room: Room,
        observerIds: Set<Uuid>,
        reasonId: String,
        diagnostic: String,
    ) {
        val expectedServer = serverHolder.current() ?: return
        withContext(dispatchers.main) {
            val server = serverHolder.current()
            val currentState = store.state.value
            if (server !== expectedServer || currentState.rooms[game.id] !== room || game.id in currentState.games) {
                logger.debug(
                    "Skipped aborted game effects gameId={} reason={}: state is no longer current",
                    game.id,
                    reasonId,
                )
                return@withContext
            }

            // 各清理步驟獨立失敗，不能因單一呈現錯誤跳過其他人的通知。
            fun runCleanupStep(name: String, block: () -> Unit) {
                try {
                    block()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    logger.error(
                        "Aborted game effect step failed gameId={} reason={} step={}",
                        game.id,
                        reasonId,
                        name,
                        exception,
                    )
                }
            }

            val location = locations.get(game.id)?.location
            if (location != null) {
                runCleanupStep("presentation") { clearPresentation(server, game.id, location) }
            } else {
                logger.warn(
                    "Could not clear aborted game presentation gameId={} reason={}: table location is unavailable",
                    game.id,
                    reasonId,
                )
            }
            runCleanupStep("busy-tracker") { busyTracker.clear(game.id) }
            runCleanupStep("opening-operations") { openingOperations.invalidate(game.id) }
            runCleanupStep("diagnostic-log") {
                logger.warn(
                    "Mahjong game aborted gameId={} matchId={} rule={} reason={} diagnostic={}",
                    game.id,
                    game.matchId,
                    moduleRegistry.getModule(game.tableState.config).id,
                    reasonId,
                    diagnostic,
                )
            }
            observerIds.forEach { playerId ->
                runCleanupStep("clear-snapshot-$playerId") {
                    serverHolder.findPlayer(playerId)?.let { player ->
                        MahjongChannels.snapshotCleared.sendTo(player, json, SnapshotClearedPayloadDto(game.id.toString()))
                    }
                }
                runCleanupStep("feedback-$playerId") {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.MatchAborted(reasonId))
                }
            }
        }
    }

    /** 清除附近仍由該桌管理的暫時實體；桌子已消失時也允許移除舊呈現。 */
    private fun clearPresentation(server: MinecraftServer, tableId: Uuid, location: TableLocation) {
        val dimension = Identifier.tryParse(location.dimensionId)
        if (dimension == null) {
            logger.warn("Could not clear aborted game presentation gameId={}: invalid dimension {}", tableId, location.dimensionId)
            return
        }
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, dimension)
        val world: ServerWorld = server.getWorld(worldKey) ?: run {
            logger.warn("Could not clear aborted game presentation gameId={}: world is unavailable", tableId)
            return
        }
        val controllerPos = BlockPos(location.x, location.y, location.z)
        val table = world.getBlockEntity(controllerPos)
        if (table is MahjongTableBlockEntity && table.tableId != tableId) {
            logger.debug("Skipped aborted game entity cleanup gameId={}: controller no longer belongs to game", tableId)
            return
        }
        val removed = presentationCleaner.clear(world, tableId, controllerPos)
        logger.debug("Cleared aborted game presentations gameId={} entityCount={}", tableId, removed)
    }
}
