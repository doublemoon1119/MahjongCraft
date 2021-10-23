package doublemoon.mahjongcraft.blockentity

import doublemoon.mahjongcraft.block.MahjongTable
import doublemoon.mahjongcraft.block.enums.MahjongTablePart
import doublemoon.mahjongcraft.client.gui.MahjongTableGui
import doublemoon.mahjongcraft.client.gui.MahjongTableWaitingScreen
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongRound
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongRule
import doublemoon.mahjongcraft.registry.BlockEntityTypeRegistry
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.client.MinecraftClient
import net.minecraft.nbt.NbtCompound

class MahjongTableBlockEntity(
    type: BlockEntityType<*> = BlockEntityTypeRegistry.mahjongTable
) : BlockEntity(type), BlockEntityClientSerializable {
    val players = arrayListOf("", "", "", "") //以玩家的 stringUUID 儲存, 先以 4 個空字串儲存, (空字串表示空位)
    val playerEntityNames = arrayListOf("", "", "", "") //以實體的 entityName 儲存
    val bots = arrayListOf(false, false, false, false) //這個玩家是否是機器人
    val ready = arrayListOf(false, false, false, false) //這個玩家是否準備了
    var rule = MahjongRule()
    var playing = false
    var round = MahjongRound() //當前的回合
    var seat = arrayListOf("", "", "", "") //座位上的玩家的 stringUUID, 按照遊戲開始的座位順序編排
    var dealer = "" //莊家的 stringUUID
    var points = arrayListOf(0, 0, 0, 0) //玩家的點數, 排列順序比照 seat

    override fun markDirty() {
        super.markDirty()
        //來自 BlockEntityClientSerializable, 使用 sync() 來同步資料, 對要同步的方塊實體 markDirty() 就好
        world?.isClient?.let { isClient ->
            if (!isClient) sync()
        }
    }

    override fun fromClientTag(tag: NbtCompound) {
        fromTag(cachedState, tag)
        world?.isClient?.let { isClient ->
            if (isClient) { //當有同步 tag 的情況出現
                val screen = MinecraftClient.getInstance().currentScreen
                if (!playing) {  //遊戲還沒開始, 刷新開著這個麻將桌視窗的玩家的視窗
                    if (screen is MahjongTableWaitingScreen && (screen.description as MahjongTableGui).mahjongTable == this) screen.refresh()
                } else {  //遊戲開始就關閉遊戲視窗
                    if (screen is MahjongTableWaitingScreen && (screen.description as MahjongTableGui).mahjongTable == this) screen.onClose()
                }
            }
        }
    }

    override fun toClientTag(tag: NbtCompound): NbtCompound = writeNbt(tag)

    override fun fromTag(state: BlockState, tag: NbtCompound) {
        super.fromTag(state, tag)
        if (state[MahjongTable.PART] == MahjongTablePart.BOTTOM_CENTER) {
            with(tag) {
                repeat(4) {
                    players[it] = getString("PlayerStringUUID$it")
                    playerEntityNames[it] = getString("PlayerEntityName$it")
                    bots[it] = getBoolean("Bot$it")
                    ready[it] = getBoolean("PlayerReady$it")
                    seat[it] = getString("Seat$it")
                    points[it] = getInt("Point$it")
                }
                dealer = getString("Dealer")
                rule = MahjongRule.fromJsonString(getString("Rule"))
                playing = getBoolean("Playing")
                round = Json.decodeFromString(MahjongRound.serializer(), getString("Round"))
            }
        }
    }

    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        if (cachedState[MahjongTable.PART] == MahjongTablePart.BOTTOM_CENTER) {
            with(nbt) {
                repeat(4) {
                    putString("PlayerStringUUID$it", players[it])
                    putString("PlayerEntityName$it", playerEntityNames[it])
                    putBoolean("Bot$it", bots[it])
                    putBoolean("PlayerReady$it", ready[it])
                    putString("Seat$it", seat[it])
                    putInt("Point$it", points[it])
                }
                putString("Dealer", dealer)
                putString("Rule", rule.toJsonString())
                putBoolean("Playing", playing)
                putString("Round", Json.encodeToString(MahjongRound.serializer(), round))
            }
        }
        return super.writeNbt(nbt)
    }

    override fun toInitialChunkDataNbt(): NbtCompound = writeNbt(NbtCompound())
}