package doublemoon.mahjongcraft.network.mahjong_game

import doublemoon.mahjongcraft.MahjongCraftClient
import doublemoon.mahjongcraft.client.gui.widget.WTileHints
import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGame
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongGameBehavior
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongTile
import doublemoon.mahjongcraft.game.mahjong.riichi.model.ScoreSettlement
import doublemoon.mahjongcraft.game.mahjong.riichi.model.YakuSettlement
import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongPlayer
import doublemoon.mahjongcraft.network.ChannelType
import doublemoon.mahjongcraft.network.CustomPayloadListener
import doublemoon.mahjongcraft.network.sendPayloadToPlayer
import doublemoon.mahjongcraft.network.sendPayloadToServer
import doublemoon.mahjongcraft.scheduler.client.ClientCountdownTimeHandler
import doublemoon.mahjongcraft.scheduler.client.OptionalBehaviorHandler
import doublemoon.mahjongcraft.scheduler.client.ScoreSettleHandler
import doublemoon.mahjongcraft.scheduler.client.YakuSettleHandler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload

/**
 * 對麻將遊戲進行操作的
 * */
object MahjongGamePayloadListener : CustomPayloadListener<MahjongGamePayload> {

    override val id: CustomPayload.Id<MahjongGamePayload> = MahjongGamePayload.ID

    override val codec: PacketCodec<RegistryByteBuf, MahjongGamePayload> = MahjongGamePayload.CODEC

    override val channelType: ChannelType = ChannelType.Both

    @Environment(EnvType.CLIENT)
    override fun onClientReceive(
        payload: MahjongGamePayload,
        context: ClientPlayNetworking.Context,
    ) {
        val (behavior, hands, target, extraData) = payload

        when (behavior) {
            MahjongGameBehavior.MACHI -> {
                WTileHints.machiOfTarget = Json.decodeFromString(extraData)
            }

            MahjongGameBehavior.COUNTDOWN_TIME -> {
                val times = Json.decodeFromString<Pair<Int?, Int?>>(extraData)
                ClientCountdownTimeHandler.basicAndExtraTime = times
            }

            MahjongGameBehavior.DISCARD -> {
                val skippable = extraData.toBoolean()
                if (skippable && MahjongCraftClient.config.quickActions.autoDrawAndDiscard) { // 自動摸切
                    sendPayloadToServer(
                        payload = MahjongGamePayload(behavior = MahjongGameBehavior.SKIP)
                    )
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
                val settlement = Json.decodeFromString<ScoreSettlement>(extraData)
                ScoreSettleHandler.start(settlement = settlement)
            }

            MahjongGameBehavior.YAKU_SETTLEMENT -> {
                val settlements = Json.decodeFromString<List<YakuSettlement>>(extraData)
                YakuSettleHandler.start(settlementList = settlements)
            }

            MahjongGameBehavior.AUTO_ARRANGE -> {
                sendPayloadToServer(
                    payload = MahjongGamePayload(
                        behavior = behavior,
                        extraData = MahjongCraftClient.config.quickActions.autoArrange.toString()
                    )
                )
            }

            else -> {
                val quickActions = MahjongCraftClient.config.quickActions
                when (behavior) {
                    MahjongGameBehavior.RON, MahjongGameBehavior.TSUMO ->
                        if (quickActions.autoCallWin) { //自動和牌
                            sendPayloadToServer(
                                payload = MahjongGamePayload(behavior = behavior)
                            )
                            return
                        }

                    MahjongGameBehavior.CHII, MahjongGameBehavior.PON_OR_CHII, MahjongGameBehavior.PON,
                    MahjongGameBehavior.ANKAN_OR_KAKAN, MahjongGameBehavior.MINKAN,
                    ->
                        if (quickActions.noChiiPonKan) { //不吃碰槓
                            sendPayloadToServer(
                                payload = MahjongGamePayload(behavior = MahjongGameBehavior.SKIP)
                            )
                            return
                        }

                    else -> {}
                }
                OptionalBehaviorHandler.start(behavior, hands, target, extraData)
            }
        }
    }

    override fun onServerReceive(
        payload: MahjongGamePayload,
        context: ServerPlayNetworking.Context,
    ) {
        val (behavior, _, _, extraData) = payload
        val player = context.player()

        val game = GameManager.getGame<MahjongGame>(player) ?: return
        val mjPlayer = game.getPlayer(player) as MahjongPlayer? ?: return

        when (behavior) {
            MahjongGameBehavior.MACHI -> {
                val tile = Json.decodeFromString<MahjongTile>(extraData)
                val machiAndHanOrigin = with(game) { mjPlayer.getMachiAndHan(tile) }
                val machiAndHan = machiAndHanOrigin.keys
                    .map { MahjongTile.entries[it.mahjong4jTile.code] } //過濾赤寶牌的情況
                    .associateWith {
                        machiAndHanOrigin[it] to with(game) {
                            mjPlayer.isFuriten(
                                tile = it,
                                machi = machiAndHanOrigin.keys.toList()
                            )
                        }
                    }

                sendPayloadToPlayer(
                    player = player,
                    payload = MahjongGamePayload(
                        behavior = MahjongGameBehavior.MACHI,
                        extraData = Json.encodeToString(machiAndHan)
                    )
                )
            }

            MahjongGameBehavior.AUTO_ARRANGE -> {
                mjPlayer.autoArrangeHands = extraData.toBoolean()
            }

            in mjPlayer.waitingBehavior -> {
                mjPlayer.behaviorResult = behavior to extraData
            }

            else -> {}
        }
    }
}