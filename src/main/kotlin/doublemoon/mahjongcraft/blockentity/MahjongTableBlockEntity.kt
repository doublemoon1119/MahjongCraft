package doublemoon.mahjongcraft.blockentity

import doublemoon.mahjongcraft.block.MahjongTable
import doublemoon.mahjongcraft.block.enums.MahjongTablePart
import doublemoon.mahjongcraft.client.gui.screen.MahjongTableGui
import doublemoon.mahjongcraft.client.gui.screen.MahjongTableWaitingScreen
import doublemoon.mahjongcraft.entity.MahjongTileEntity
import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGame
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongRound
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongRule
import doublemoon.mahjongcraft.network.mahjong_table.MahjongTablePayloadListener
import doublemoon.mahjongcraft.registry.BlockEntityTypeRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.mahjong4j.tile.Tile
import java.util.concurrent.ConcurrentHashMap

class MahjongTableBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(BlockEntityTypeRegistry.mahjongTable, pos, state) {
    private var gameInitialized = false
    val players = arrayListOf("", "", "", "") //以玩家的 stringUUID 儲存, 先以 4 個空字串儲存, (空字串表示空位)
    val playerEntityNames = arrayListOf("", "", "", "") //以實體的 entityName 儲存
    val bots = arrayListOf(false, false, false, false) //這個玩家是否是機器人
    val ready = arrayListOf(false, false, false, false) //這個玩家是否準備了
    var rule = MahjongRule() //這是為了能讓 rule 能夠儲存在桌子上, 直到開啟麻將桌時套用到新遊戲上
    var playing = false
    var round = MahjongRound() //當前的回合
    var seat = arrayListOf("", "", "", "") //座位上的玩家的 stringUUID, 按照遊戲開始的座位順序編排
    var dealer = "" //莊家的 stringUUID
    var points = arrayListOf(0, 0, 0, 0) //玩家的點數, 排列順序比照 seat

    /**
     * 剩餘的牌, index 為 [Tile.code], value 為剩下的張數,
     * 這裡存的資料是只有面向客戶端的, 每個人看到剩下的張數都不一樣, 要經過計算才能確定
     * */
    val remainingTiles = ConcurrentHashMap<Int, Int>().apply {
        Tile.entries.forEach { this[it.code] = 0 }
    }

    /**
     * 從桌子中心取得牌桌大小內所有 牌的實體,
     * 經過計算後修正 [remainingTiles]
     * */
    @Environment(EnvType.CLIENT)
    fun calculateRemainingTiles(code: Int) {
        val tableCenter = with(this.pos) { Vec3d(x + 0.5, y + 1.0, z + 0.5) }
        val tiles = world?.getEntitiesByClass(
            MahjongTileEntity::class.java,
            Box.of(tableCenter, 3.0, 2.0, 3.0)
        ) { it.isSpawnedByGame && it.gameBlockPos == this.pos && it.mahjongTile.code == code } ?: return
        remainingTiles[code] = 4 - tiles.size
    }

    override fun markDirty() {
        super.markDirty()
        //對伺服端要同步的方塊實體 markDirty() 就好
        if (this.hasWorld() && !this.world!!.isClient) {
            (world as ServerWorld).chunkManager.markForUpdate(getPos())
        }
    }

    override fun readNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup) {
        with(nbt) {
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
            round = Json.decodeFromString(getString("Round"))
        }
        world?.isClient?.let { isClient ->
            if (isClient) { //當有同步 tag 的情況出現
                val screen = MinecraftClient.getInstance().currentScreen
                if (screen is MahjongTableWaitingScreen && (screen.description as MahjongTableGui).mahjongTable == this) {
                    if (!playing) {  //遊戲還沒開始, 刷新開著這個麻將桌視窗的玩家的視窗
                        screen.refresh()
                    } else {  //遊戲開始就關閉遊戲視窗
                        screen.close()
                    }
                }
            }
        }
    }

    override fun writeNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup) {
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
                putString("Round", Json.encodeToString(round))
            }
        }
    }

    override fun toInitialChunkDataNbt(registryLookup: RegistryWrapper.WrapperLookup): NbtCompound =
        NbtCompound().also { writeNbt(it, registryLookup) }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)

    companion object {
        fun tick(world: World, pos: BlockPos, blockEntity: MahjongTableBlockEntity) {
            if (!world.isClient && !blockEntity.gameInitialized) {
                MahjongTablePayloadListener.syncBlockEntityDataWithGame(
                    blockEntity = blockEntity,
                    game = GameManager.getGameOrDefault(
                        world = world as ServerWorld,
                        pos = pos,
                        default = MahjongGame(world, pos, blockEntity.rule)
                    )
                )
                blockEntity.gameInitialized = true
            }
        }
    }
}