package doublemoon.mahjongcraft.network.mahjong_table

import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.client.gui.screen.MahjongTableWaitingScreen
import doublemoon.mahjongcraft.client.gui.screen.RuleEditorScreen
import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.game.GameStatus
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGame
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongRule
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongTableBehavior
import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongBot
import doublemoon.mahjongcraft.logger
import doublemoon.mahjongcraft.network.ChannelType
import doublemoon.mahjongcraft.network.CustomPayloadListener
import doublemoon.mahjongcraft.network.sendPayloadToPlayer
import doublemoon.mahjongcraft.scheduler.client.ClientScheduler
import doublemoon.mahjongcraft.scheduler.server.ServerScheduler
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

/**
 * 對麻將桌進行操作的
 * */
object MahjongTablePayloadListener : CustomPayloadListener<MahjongTablePayload> {

    override val id: CustomPayload.Id<MahjongTablePayload> = MahjongTablePayload.ID

    override val codec: PacketCodec<RegistryByteBuf, MahjongTablePayload> = MahjongTablePayload.CODEC

    override val channelType: ChannelType = ChannelType.Both

    @Environment(EnvType.CLIENT)
    override fun onClientReceive(
        payload: MahjongTablePayload,
        context: ClientPlayNetworking.Context,
    ) {
        val (behavior, pos, _) = payload
        val client = context.client()
        val world = client.world ?: return

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

    override fun onServerReceive(
        payload: MahjongTablePayload,
        context: ServerPlayNetworking.Context,
    ) {
        val (behavior, pos, extraData) = payload
        val player = context.player()
        val world = player.world as ServerWorld? ?: return

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
                    sendPayloadToPlayer(
                        player = player,
                        payload = MahjongTablePayload(
                            behavior = MahjongTableBehavior.OPEN_RULES_EDITOR_GUI,
                            pos = pos,
                            extraData = game.rule.toJsonString()
                        )
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

    /**
     * 對 [MahjongTableBlockEntity] 設置改變,
     * 同步伺服端與客戶端的麻將桌
     * */
    private fun syncBlockEntityWithGame(
        invokeOnNextTick: Boolean = true,
        world: ServerWorld,
        pos: BlockPos,
        apply: MahjongGame.() -> Unit = {},
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
        apply: MahjongGame.() -> Unit = {},
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
                    this.playerEntityNames[i] = it.players.getOrNull(i)?.entity?.name?.string ?: ""
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