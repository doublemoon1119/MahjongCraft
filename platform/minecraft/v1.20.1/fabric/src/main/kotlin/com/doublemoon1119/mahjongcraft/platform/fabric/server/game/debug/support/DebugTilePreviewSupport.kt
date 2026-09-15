package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.normalizedTileAssetKey
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 供各 debug command family 共用的臨時牌 entity 與 `tile` 引數支援。
 *
 * 這裡生成的牌刻意不呼叫 `assignToTable`（[MahjongTileEntity.managedByGame] 維持 `false`），client 端
 * `MahjongTileEntityRenderer` 對「自由放置」的牌一律直接讀 entity 自身的 `tileAssetKey`，不像牌局管理中
 * 的牌那樣需要透過 `TableStateSnapshot` 可見性快照才能解析牌面，因此不需要真正的對局資料也能正確顯示
 * 指定的牌面。
 *
 * 本類別不保存任何 command state，也不依賴 debug root 或任何 family command。
 *
 * @property tileAssetRegistry 解析與補全牌面 asset key 的來源，含第三方註冊的 asset key。
 */
@Single
class DebugTilePreviewSupport(private val tileAssetRegistry: MinecraftTileAssetRegistry) {
    /**
     * 幫一個子指令節點同時掛上「不帶 `tile` 引數」與「帶 `tile` 引數」兩種執行路徑——省略引數時
     * [onExecute] 收到的 `tileArg` 為 `null`，由呼叫端自行決定預設牌面，見 [resolveAssetKey]。
     *
     * 這裡的 `tile` 是全域固定的素材 asset key（見 [suggestTileAssetKeys]），單純選擇要渲染成什麼
     * 牌面，與任何玩家手牌無關；跟 `FabricGameCommand` 的 `tile` 引數（依玩家當下手牌動態算出的
     * candidate token，對應一張實體牌）是兩個不同概念，不應合併實作。
     */
    fun withOptionalTileArgument(
        node: LiteralArgumentBuilder<ServerCommandSource>,
        onExecute: (ServerCommandSource, String?) -> Int,
    ): LiteralArgumentBuilder<ServerCommandSource> = node
        .executes { ctx -> onExecute(ctx.source, null) }
        .then(
            argument(TILE_ARGUMENT, StringArgumentType.word())
                .suggests(::suggestTileAssetKeys)
                .executes { ctx -> onExecute(ctx.source, StringArgumentType.getString(ctx, TILE_ARGUMENT)) },
        )

    /** 補全 `tile` 引數：內建 asset key（排除佔位用的 [UNKNOWN_TILE_ASSET_KEY]）與已註冊的第三方 asset key。 */
    fun suggestTileAssetKeys(
        @Suppress("UNUSED_PARAMETER") context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        buildTileAssetKeySuggestions(builder.remaining, tileAssetRegistry.registeredAssetKeys).forEach(builder::suggest)
        return builder.buildFuture()
    }

    /** 省略 `tile` 引數時隨機抽一個內建牌面（排除佔位用的 `unknown`），否則正規化呼叫者輸入的字串。 */
    fun resolveAssetKey(tileArg: String?): String = tileArg?.normalizedTileAssetKey(tileAssetRegistry)
        ?: ALL_TILE_ASSET_KEYS.dropLast(1).random()

    /** 生成一張自由放置（不掛任何桌子/對局）的臨時牌 entity，理由見類別 KDoc 的牌面說明。 */
    fun spawnFreeTile(
        world: ServerWorld,
        placement: MahjongTileWallPlacement,
        pose: MahjongTilePose,
        assetKey: String,
    ): MahjongTileEntity {
        val tile = MahjongTileEntity(world = world).apply {
            uuid = Uuid.random().toJavaUuid()
            refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            tilePose = pose
            tileAssetKey = assetKey
        }
        world.spawnEntity(tile)
        return tile
    }

    companion object {
        /** `tile` 引數名稱。 */
        const val TILE_ARGUMENT: String = "tile"
    }
}

/**
 * 依目前輸入內容建立 `tile` 引數的補全候選；內建 asset key 依既有順序優先，其後接已註冊的第三方
 * asset key，佔位用的 [UNKNOWN_TILE_ASSET_KEY] 一律排除。
 */
internal fun buildTileAssetKeySuggestions(
    remaining: String,
    registeredAssetKeys: Collection<String>,
): List<String> = (ALL_TILE_ASSET_KEYS.asSequence().filterNot { it == UNKNOWN_TILE_ASSET_KEY } + registeredAssetKeys)
    .distinct()
    .filter { it.startsWith(remaining, ignoreCase = true) }
    .toList()
