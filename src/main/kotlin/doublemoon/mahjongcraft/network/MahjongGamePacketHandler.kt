package doublemoon.mahjongcraft.network

import doublemoon.mahjongcraft.MahjongCraftClient
import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.game.mahjong.riichi.*
import doublemoon.mahjongcraft.id
import doublemoon.mahjongcraft.scheduler.client.ClientCountdownTimeHandler
import doublemoon.mahjongcraft.scheduler.client.OptionalBehaviorHandler
import doublemoon.mahjongcraft.scheduler.client.ScoreSettleHandler
import doublemoon.mahjongcraft.scheduler.client.YakuSettleHandler
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

/**
 * 對麻將遊戲進行操作的
 * */
object MahjongGamePacketHandler : CustomPacketHandler {

    override val channelName = id("mahjong_game_packet")

    private data class MahjongGamePacket(
        val behavior: MahjongGameBehavior,
        val hands: List<MahjongTile>,
        val target: ClaimTarget,
        val extraData: String
    ) : CustomPacket {
        constructor(byteBuf: PacketByteBuf) : this(
            behavior = byteBuf.readEnumConstant(MahjongGameBehavior::class.java),
            hands = Json.decodeFromString<MutableList<MahjongTile>>(byteBuf.readString(Short.MAX_VALUE.toInt())),
            target = byteBuf.readEnumConstant(ClaimTarget::class.java),
            extraData = byteBuf.readString(Short.MAX_VALUE.toInt())
        )

        override fun writeByteBuf(byteBuf: PacketByteBuf) {
            with(byteBuf) {
                writeEnumConstant(behavior)
                writeString(Json.encodeToString(hands), Short.MAX_VALUE.toInt())
                writeEnumConstant(target)
                writeString(extraData, Short.MAX_VALUE.toInt())
            }
        }
    }

    @Environment(EnvType.CLIENT)
    fun ClientPlayerEntity.sendMahjongGamePacket(
        behavior: MahjongGameBehavior,
        hands: List<MahjongTile> = listOf(),
        target: ClaimTarget = ClaimTarget.SELF,
        extraData: String = "",
    ) = this.networkHandler.sendPacket(
        ClientPlayNetworking.createC2SPacket(
            channelName,
            MahjongGamePacket(behavior, hands, target, extraData).createByteBuf()
        )
    )

    fun ServerPlayerEntity.sendMahjongGamePacket(
        behavior: MahjongGameBehavior,
        hands: List<MahjongTile> = listOf(),
        target: ClaimTarget = ClaimTarget.SELF,
        extraData: String = "",
    ) = this.networkHandler.sendPacket(
        ServerPlayNetworking.createS2CPacket(
            channelName,
            MahjongGamePacket(behavior, hands, target, extraData).createByteBuf()
        )
    )

    @Environment(EnvType.CLIENT)
    override fun onClientReceive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
        MahjongGamePacket(byteBuf).apply {
            when (behavior) {
                MahjongGameBehavior.COUNTDOWN_TIME -> {
                    val times = Json.decodeFromString<Pair<Int?, Int?>>(extraData)
                    ClientCountdownTimeHandler.basicAndExtraTime = times
                }
                MahjongGameBehavior.DISCARD -> {
                    val skippable = extraData.toBoolean()
                    if (skippable && MahjongCraftClient.config.quickActions.autoDrawAndDiscard) { //自動摸切
                        client.player?.sendMahjongGamePacket(behavior = MahjongGameBehavior.SKIP)
                    }
                }
                MahjongGameBehavior.GAME_START -> {
                    MahjongCraftClient.playing = true
                }
                MahjongGameBehavior.GAME_OVER -> {
                    MahjongCraftClient.playing = false
                    OptionalBehaviorHandler.cancel()
                }
                MahjongGameBehavior.SCORE_SETTLEMENT -> {
                    with(MahjongCraftClient.config.quickActions) { //每個回合結束的時候, 重置這三個設定
                        autoCallWin = false
                        noChiiPonKan = false
                        autoDrawAndDiscard = false
                        MahjongCraftClient.saveConfig()
                    }
                    val settlement = Json.decodeFromString(ScoreSettlement.serializer(), extraData)
                    ScoreSettleHandler.start(settlement = settlement)
                }
                MahjongGameBehavior.YAKU_SETTLEMENT -> {
                    val settlements = Json.decodeFromString<List<YakuSettlement>>(extraData)
                    YakuSettleHandler.start(settlementList = settlements)
                }
                MahjongGameBehavior.AUTO_ARRANGE -> {
                    client.player?.sendMahjongGamePacket(
                        behavior,
                        extraData = MahjongCraftClient.config.quickActions.autoArrange.toString()
                    )
                }
                else -> {
                    val quickActions = MahjongCraftClient.config.quickActions
                    when (behavior) {
                        MahjongGameBehavior.RON, MahjongGameBehavior.TSUMO ->
                            if (quickActions.autoCallWin) { //自動和牌
                                client.player?.sendMahjongGamePacket(behavior = behavior)
                                return
                            }
                        MahjongGameBehavior.CHII, MahjongGameBehavior.PON_OR_CHII, MahjongGameBehavior.PON,
                        MahjongGameBehavior.ANKAN_OR_KAKAN, MahjongGameBehavior.MINKAN ->
                            if (quickActions.noChiiPonKan) { //不吃碰槓
                                client.player?.sendMahjongGamePacket(behavior = MahjongGameBehavior.SKIP)
                                return
                            }
                        else -> {}
                    }
                    OptionalBehaviorHandler.start(
                        behavior,
                        hands,
                        target,
                        extraData
                    )
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
        MahjongGamePacket(byteBuf).apply {
            val game = GameManager.getGame<MahjongGame>(player) ?: return@apply
            val mjPlayer = game.getPlayer(player) as MahjongPlayer? ?: return@apply
            if (behavior == MahjongGameBehavior.AUTO_ARRANGE) {
                mjPlayer.autoArrangeHands = extraData.toBoolean()
            } else if (behavior in mjPlayer.waitingBehavior) {
                mjPlayer.behaviorResult = behavior to extraData
            }
        }
    }
}