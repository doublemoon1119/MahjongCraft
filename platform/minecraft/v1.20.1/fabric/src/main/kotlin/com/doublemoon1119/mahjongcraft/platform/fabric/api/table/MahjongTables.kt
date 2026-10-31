package com.doublemoon1119.mahjongcraft.platform.fabric.api.table

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableLookup
import com.doublemoon1119.mahjongcraft.platform.fabric.block.playSpaceCells
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSpace
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * 麻將桌方塊在自己的回呼中呼叫的 MahjongCraft 功能。
 *
 * 內建桌子與第三方桌子都經由這些函式開啟房間畫面與清理整局呈現，不需要繼承內建桌子類別。
 */
object MahjongTables {
    /** 記錄桌子找不到控制點等異常狀況。 */
    private val logger = mahjongCraftLogger(MahjongTables::class)

    /** MahjongCraft 初始化時提供的服務；初始化前為 null。 */
    @Volatile
    private var services: Services? = null

    /**
     * 右鍵桌子任何一格時呼叫，開啟房間畫面；取代方塊 `onUse` 的內容。
     *
     * 客戶端一律回傳成功，讓手部動作正常顯示；伺服器依 [MahjongTableStructureBlock] 找到控制點後開啟畫面。
     *
     * @param world 方塊所在世界。
     * @param pos 被右鍵的方塊位置。
     * @param state 被右鍵的方塊狀態。
     * @param player 右鍵的玩家。
     * @return 開啟房間畫面或在客戶端時為成功；方塊不屬於可用的桌子時為失敗。
     */
    fun interact(
        world: World,
        pos: BlockPos,
        state: BlockState,
        player: PlayerEntity,
    ): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        val serverPlayer = player as? ServerPlayerEntity ?: return ActionResult.FAIL
        val structure = state.block as? MahjongTableStructureBlock ?: return ActionResult.FAIL
        val controllerPos = structure.mahjongTableControllerPos(world, pos, state) ?: return ActionResult.FAIL
        val table = MahjongTableLookup.find(world, controllerPos)
        if (table == null) {
            logger.warn("Rejected interaction with Mahjong table block at {}: no controller at {}", pos, controllerPos)
            return ActionResult.FAIL
        }
        val roomService = requireServices().roomService
        roomService.openRoomScreen(table, serverPlayer)
        return ActionResult.SUCCESS
    }

    /**
     * 控制方塊被取代或拆除時呼叫，清理這張桌子的整局呈現並交由 MahjongCraft 處理其房間或對局。
     *
     * 必須在控制方塊的方塊實體仍存在時呼叫，例如方塊 `onStateReplaced` 中呼叫 `super` 之前；同一次移除只呼叫一次。
     * 客戶端呼叫不做任何事。
     *
     * @param world 控制方塊所在世界。
     * @param controllerPos 控制方塊位置。
     */
    fun onControllerRemoved(
        world: World,
        controllerPos: BlockPos,
    ) {
        if (world !is ServerWorld) return
        val table = MahjongTableLookup.find(world, controllerPos)
        if (table == null) {
            logger.warn("Skipped Mahjong table removal at {}: no controller block entity", controllerPos)
            return
        }
        requireServices().tableLifecycleService.onBlockReplaced(world, table)
    }

    /**
     * 列出必須保持空著的擺牌層：控制方塊上方一格、以其為中心的 3×3。
     *
     * 牌、牌牆、骰子、桌面物件與選牌確認都在這一層。桌子自己的方塊可以進入這一層（例如桌緣），
     * 但不能擋住擺牌。
     *
     * @param controllerPos 控制方塊位置。
     * @return 擺牌層的 9 個方塊位置。
     */
    fun playSpaceCells(controllerPos: BlockPos): List<BlockPos> = MahjongTableSpace.playSpaceCells(controllerPos)

    /**
     * 由 MahjongCraft 初始化時提供桌子功能所需的服務。
     *
     * @param roomService 開啟房間畫面的服務。
     * @param tableLifecycleService 清理桌子呈現與生命週期的服務。
     */
    internal fun install(
        roomService: MahjongTableRoomService,
        tableLifecycleService: FabricTableLifecycleService,
    ) {
        services = Services(roomService, tableLifecycleService)
    }

    /** @return 已提供的服務；MahjongCraft 尚未初始化時拋出例外。 */
    private fun requireServices(): Services = checkNotNull(services) { "MahjongCraft has not initialized its table services yet" }

    /**
     * 桌子功能使用的 MahjongCraft 服務。
     *
     * @property roomService 開啟房間畫面的服務。
     * @property tableLifecycleService 清理桌子呈現與生命週期的服務。
     */
    private class Services(
        val roomService: MahjongTableRoomService,
        val tableLifecycleService: FabricTableLifecycleService,
    )
}
