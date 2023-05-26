package doublemoon.mahjongcraft.network

import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.client.gui.MahjongTableWaitingScreen
import doublemoon.mahjongcraft.client.gui.RuleEditorScreen
import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.game.GameStatus
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongBot
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGame
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongRule
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongTableBehavior
import doublemoon.mahjongcraft.id
import doublemoon.mahjongcraft.logger
import doublemoon.mahjongcraft.scheduler.client.ClientScheduler
import doublemoon.mahjongcraft.scheduler.server.ServerScheduler
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

/**
 * 對麻將桌進行操作的
 * */
object MahjongTablePacketListener : CustomPacketListener {

    override val channelName = id("mahjong_table_packet")

    private data class MahjongTablePacket(
        val behavior: MahjongTableBehavior,
        val pos: BlockPos,
        val extraData: String
    ) : CustomPacket {
        constructor(byteBuf: PacketByteBuf) : this(
            behavior = byteBuf.readEnumConstant(MahjongTableBehavior::class.java),
            pos = byteBuf.readBlockPos(),
            extraData = byteBuf.readString(Short.MAX_VALUE.toInt())
        )

        override fun writeByteBuf(byteBuf: PacketByteBuf) {
            with(byteBuf) {
                writeEnumConstant(behavior)
                writeBlockPos(pos)
                writeString(extraData, Short.MAX_VALUE.toInt())
            }
        }
    }

    @Environment(EnvType.CLIENT)
    fun ClientPlayerEntity.sendMahjongTablePacket(
        behavior: MahjongTableBehavior,
        pos: BlockPos,
        extraData: String = ""
    ) = this.networkHandler.sendPacket(
        ClientPlayNetworking.createC2SPacket(
            channelName,
            MahjongTablePacket(behavior, pos, extraData).createByteBuf()
        )
    )

    fun ServerPlayerEntity.sendMahjongTablePacket(
        behavior: MahjongTableBehavior,
        pos: BlockPos,
        extraData: String = ""
    ) = this.networkHandler.sendPacket(
        ServerPlayNetworking.createS2CPacket(
            channelName,
            MahjongTablePacket(behavior, pos, extraData).createByteBuf()
        )
    )

    @Environment(EnvType.CLIENT)
    override fun onClientReceive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
        MahjongTablePacket(byteBuf).apply {
            val world = client.world!!
            when (behavior) {
                //加入 GUI 參考自 https://github.com/CottonMC/LibGui/wiki/Getting-Started-with-GUIs
                MahjongTableBehavior.OPEN_TABLE_WAITING_GUI -> {  //開啟麻將桌等待中的 GUI
                    ClientScheduler.scheduleDelayAction {
                        val mahjongTableBlockEntity =
                            world.getBlockEntity(pos) as MahjongTableBlockEntity? ?: return@scheduleDelayAction
                        client.setScreen(MahjongTableWaitingScreen(mahjongTable = mahjongTableBlockEntity))
                    }
                }
                MahjongTableBehavior.OPEN_RULES_EDITOR_GUI -> {  //開啟規則編輯器 GUI
                    ClientScheduler.scheduleDelayAction {
                        val mahjongTableBlockEntity =
                            world.getBlockEntity(pos) as MahjongTableBlockEntity? ?: return@scheduleDelayAction
                        client.setScreen(RuleEditorScreen(mahjongTable = mahjongTableBlockEntity))
                    }
                }
                else -> {
                }
            }
        }
    }

    override fun onServerReceive(
        server: MinecraftServer,
        player: ServerPlayerEntity,
        handler: ServerPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
        MahjongTablePacket(byteBuf).apply {
            val world = player.world as ServerWorld
            when (behavior) {
                MahjongTableBehavior.JOIN -> syncBlockEntityWithGame(world = world, pos = pos) {  //讓玩家加入遊戲
                    if (status == GameStatus.WAITING) join(player)
                }
                MahjongTableBehavior.LEAVE -> syncBlockEntityWithGame(world = world, pos = pos) { //讓玩家離開遊戲
                    if (status == GameStatus.WAITING) leave(player)
                }
                MahjongTableBehavior.READY -> syncBlockEntityWithGame(world = world, pos = pos) { //讓玩家準備遊戲
                    if (status == GameStatus.WAITING) readyOrNot(player, ready = true)
                }
                MahjongTableBehavior.NOT_READY -> syncBlockEntityWithGame(world = world, pos = pos) { //讓玩家取消準備遊戲
                    if (status == GameStatus.WAITING) readyOrNot(player, ready = false)
                }
                MahjongTableBehavior.START -> { //開始遊戲
                    val game = GameManager.getGame<MahjongGame>(world, pos)
                    if (game != null && game.isHost(player) && game.status == GameStatus.WAITING) {
                        game.start()
                    }
                }
                MahjongTableBehavior.KICK -> syncBlockEntityWithGame(world = world, pos = pos) { //讓主持剔除玩家
                    if (isHost(player) && status == GameStatus.WAITING) kick(index = extraData.toInt())
                }
                MahjongTableBehavior.ADD_BOT -> syncBlockEntityWithGame(world = world, pos = pos) { //新增機器人到遊戲
                    if (isHost(player) && status == GameStatus.WAITING) addBot()
                }
                MahjongTableBehavior.OPEN_RULES_EDITOR_GUI -> { //玩家請求開啟規則編輯器
                    val game = GameManager.getGame<MahjongGame>(world, pos)
                    if (game != null && game.isHost(player) && game.status == GameStatus.WAITING) {
                        player.sendMahjongTablePacket(
                            behavior = MahjongTableBehavior.OPEN_RULES_EDITOR_GUI,
                            pos = pos,
                            extraData = game.rule.toJsonString()
                        )
                    }
                }
                MahjongTableBehavior.CHANGE_RULE -> syncBlockEntityWithGame(world = world, pos = pos) { //改變規則
                    if (isHost(player) && status == GameStatus.WAITING) changeRules(MahjongRule.fromJsonString(extraData))
                }
                else -> {
                }
            }
        }
    }

    /**
     * 對 [MahjongTableBlockEntity] 設置改變,
     * 同步伺服端與客戶端的麻將桌
     * */
    private fun syncBlockEntityWithGame(
        invokeOnNextTick: Boolean = true,
        world: ServerWorld,
        pos: BlockPos,
        apply: MahjongGame.() -> Unit = {}
    ) {
        val game = GameManager.getGame<MahjongGame>(world, pos) ?: return
        syncBlockEntityWithGame(invokeOnNextTick, game, apply)
    }

    /**
     * 對 [MahjongTableBlockEntity] 設置改變,
     * 同步伺服端與客戶端的麻將桌
     *
     * 注意:
     * - [ServerWorld.getBlockEntity] 必須在主線程上調用, 否則會為 null, 這裡 [invokeOnNextTick] 會將任務調度到下個 tick 執行
     * - 在伺服器世界中生成 Entity 時, 也要在主線程上調用, 否則可能會有小概率出錯
     * */
    fun syncBlockEntityWithGame(
        invokeOnNextTick: Boolean = true,
        game: MahjongGame,
        apply: MahjongGame.() -> Unit = {}
    ) {
        val syncAction = {
            game.apply()
            val world = game.world
            val pos = game.pos
            val blockEntity = world.getBlockEntity(pos) as MahjongTableBlockEntity?
            if (blockEntity != null) {
                syncBlockEntityDataWithGame(blockEntity, game)
            } else {
                logger.error("Cannot find a MahjongTableBlockEntity at (world=$world,pos=$pos)")
            }
        }
        if (invokeOnNextTick) {
            ServerScheduler.scheduleDelayAction { syncAction.invoke() }
        } else {
            syncAction.invoke()
        }
    }

    /**
     * 對 [blockEntity] 同步 [game] 的資料
     * */
    fun syncBlockEntityDataWithGame(
        blockEntity: MahjongTableBlockEntity,
        game: MahjongGame,
    ) {
        with(blockEntity) {
            game.also {
                repeat(4) { i ->
                    this.players[i] = it.players.getOrNull(i)?.uuid ?: ""
                    this.playerEntityNames[i] = it.players.getOrNull(i)?.entity?.entityName ?: ""
                    this.bots[i] = it.players.getOrNull(i) is MahjongBot
                    this.ready[i] = it.players.getOrNull(i)?.ready ?: false
                    this.seat[i] = it.seat.getOrNull(i)?.uuid ?: ""
                    this.points[i] = it.seat.getOrNull(i)?.points ?: 0
                }
                this.rule = it.rule
                this.playing = it.status == GameStatus.PLAYING
                this.round = it.round
                this.dealer = it.seat.getOrNull(it.round.round)?.uuid ?: ""
            }
            this.markDirty()
        }
    }
}