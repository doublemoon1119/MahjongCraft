package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * 載入器提供的伺服器端事件。
 *
 * 每個方法登記一個監聽器，登記後一直有效；監聽器都在伺服器主執行緒上呼叫，只會收到伺服器端的世界與玩家。
 * 各載入器各自實作，把自己的事件轉成這裡的呼叫。
 */
interface ServerEvents {
    /**
     * 伺服器啟動完成、開始接受玩家之後。
     *
     * @param listener 收到啟動完成的伺服器。
     */
    fun onServerStarted(listener: (MinecraftServer) -> Unit)

    /**
     * 伺服器開始關閉、世界尚未存檔之前。
     *
     * @param listener 收到正在關閉的伺服器。
     */
    fun onServerStopping(listener: (MinecraftServer) -> Unit)

    /**
     * 伺服器完全關閉之後。
     *
     * @param listener 收到已關閉的伺服器。
     */
    fun onServerStopped(listener: (MinecraftServer) -> Unit)

    /**
     * 每個伺服器 tick 處理完所有世界之後。
     *
     * @param listener 收到剛跑完一個 tick 的伺服器。
     */
    fun onServerTickEnd(listener: (MinecraftServer) -> Unit)

    /**
     * 玩家加入伺服器、已經可以收送封包之後。
     *
     * @param listener 收到加入的玩家。
     */
    fun onPlayerJoin(listener: (ServerPlayer) -> Unit)

    /**
     * 玩家離線時；玩家的連線物件仍是離線前的那一個。
     *
     * @param listener 收到離線的玩家。
     */
    fun onPlayerDisconnect(listener: (ServerPlayer) -> Unit)

    /**
     * 區塊載入到世界之後。
     *
     * @param listener 收到區塊所在的世界與區塊。
     */
    fun onChunkLoad(listener: (ServerLevel, LevelChunk) -> Unit)

    /**
     * 區塊從世界卸載時。
     *
     * @param listener 收到區塊所在的世界與區塊。
     */
    fun onChunkUnload(listener: (ServerLevel, LevelChunk) -> Unit)

    /**
     * 實體加入世界之後，包括新生成與隨區塊載入。
     *
     * @param listener 收到實體與所在世界。
     */
    fun onEntityLoad(listener: (Entity, ServerLevel) -> Unit)

    /**
     * 實體離開世界時，包括被移除與隨區塊卸載。
     *
     * @param listener 收到實體與所在世界。
     */
    fun onEntityUnload(listener: (Entity, ServerLevel) -> Unit)

    /**
     * 方塊實體加入世界之後，包括新放置與隨區塊載入。
     *
     * @param listener 收到方塊實體與所在世界。
     */
    fun onBlockEntityLoad(listener: (BlockEntity, ServerLevel) -> Unit)

    /**
     * 玩家的客戶端開始看到某個實體時。
     *
     * @param listener 收到被看到的實體與玩家。
     */
    fun onStartTracking(listener: (Entity, ServerPlayer) -> Unit)

    /**
     * 玩家的客戶端不再看到某個實體時。
     *
     * @param listener 收到不再被看到的實體與玩家。
     */
    fun onStopTracking(listener: (Entity, ServerPlayer) -> Unit)

    /**
     * 玩家破壞方塊之前；任一監聽器回傳 `false` 就取消這次破壞。
     *
     * @param listener 收到世界、玩家、位置與方塊狀態，回傳是否允許破壞。
     */
    fun onBeforePlayerBreakBlock(listener: (ServerLevel, ServerPlayer, BlockPos, BlockState) -> Boolean)

    /**
     * 玩家成功破壞方塊之後。
     *
     * @param listener 收到世界、玩家、位置、破壞前的方塊狀態，以及破壞前的方塊實體（沒有時為 `null`）。
     */
    fun onAfterPlayerBreakBlock(listener: (ServerLevel, ServerPlayer, BlockPos, BlockState, BlockEntity?) -> Unit)

    /**
     * 伺服器建立指令表時；每次重新載入資料包等需要重建指令表時都會再呼叫。
     *
     * @param listener 收到要登記指令的指令分派器。
     */
    fun onRegisterCommands(listener: (CommandDispatcher<CommandSourceStack>) -> Unit)

    /**
     * 玩家對實體按下使用鍵時；回傳 [InteractionResult.PASS] 交給其他處理，其他結果代表已處理並結束這次互動。
     *
     * @param listener 收到玩家、世界、使用的手與被點擊的實體，回傳處理結果。
     */
    fun onPlayerUseEntity(listener: (ServerPlayer, ServerLevel, InteractionHand, Entity) -> InteractionResult)
}
