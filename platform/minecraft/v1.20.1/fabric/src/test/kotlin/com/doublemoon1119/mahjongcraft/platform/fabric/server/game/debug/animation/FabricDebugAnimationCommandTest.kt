package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.animation

import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugTilePreviewSupport
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayoutFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.CommandNode
import net.minecraft.server.command.ServerCommandSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證自由動畫子指令樹的 literal、參數結構與 tile 補全候選。 */
class FabricDebugAnimationCommandTest {
    private val command = FabricDebugAnimationCommand(
        tilePreviewSupport = DebugTilePreviewSupport(MinecraftTileAssetRegistryImpl()),
        layoutFactory = DebugVirtualTableLayoutFactory(),
        entityLifecycle = DebugPreviewEntityLifecycle(),
    )

    /** `dice` 保留預設執行路徑與兩個固定骰數子節點。 */
    @Test
    fun `dice keeps the default and fixed count literals`() {
        val dice = command.buildDiceCommand().build()

        assertEquals(FabricDebugAnimationCommand.DICE_SUBCOMMAND, dice.name)
        assertNotNull(dice.command, "runs without an explicit dice count")
        assertEquals(setOf("2", "3"), dice.children.map(CommandNode<ServerCommandSource>::getName).toSet())
    }

    /** 單張牌動畫子指令都同時提供「不帶 tile」與「帶 tile」兩種路徑。 */
    @Test
    fun `single tile animations expose an optional tile argument`() {
        val nodes = mapOf(
            FabricDebugAnimationCommand.DEAL_SUBCOMMAND to command.buildDealCommand(),
            FabricDebugAnimationCommand.DRAW_SUBCOMMAND to command.buildDrawCommand(),
            FabricDebugAnimationCommand.DISCARD_SUBCOMMAND to command.buildDiscardCommand(),
        )

        nodes.forEach { (name, builder) ->
            assertOptionalTileArgument(name, builder)
        }
    }

    /** `meld` 保留四種鳴牌 literal，且每個 literal 各自帶可選 tile 引數。 */
    @Test
    fun `meld keeps every claim literal with its own tile argument`() {
        val meld = command.buildMeldCommand().build()

        assertEquals(FabricDebugAnimationCommand.MELD_SUBCOMMAND, meld.name)
        assertEquals(
            setOf(
                FabricDebugAnimationCommand.CHI_ARGUMENT,
                FabricDebugAnimationCommand.PON_ARGUMENT,
                FabricDebugAnimationCommand.KAN_ARGUMENT,
                FabricDebugAnimationCommand.ADDED_KAN_ARGUMENT,
            ),
            meld.children.map(CommandNode<ServerCommandSource>::getName).toSet(),
        )
        meld.children.forEach { claim ->
            assertNotNull(claim.command, "${claim.name} runs without a tile argument")
            assertEquals(
                setOf(DebugTilePreviewSupport.TILE_ARGUMENT),
                claim.children.map(CommandNode<ServerCommandSource>::getName).toSet(),
                "${claim.name} exposes exactly one tile argument",
            )
        }
    }

    /** 動畫子指令不共用同一個 Brigadier 節點實例，避免掛載後互相污染。 */
    @Test
    fun `builds independent nodes on every call`() {
        assertTrue(command.buildDealCommand() !== command.buildDealCommand())
    }

    /** 檢查一個 literal 同時具備直接執行路徑與單一 tile 引數子節點。 */
    private fun assertOptionalTileArgument(
        name: String,
        builder: LiteralArgumentBuilder<ServerCommandSource>,
    ) {
        val node = builder.build()

        assertEquals(name, node.name)
        assertNotNull(node.command, "$name runs without a tile argument")
        assertEquals(
            setOf(DebugTilePreviewSupport.TILE_ARGUMENT),
            node.children.map(CommandNode<ServerCommandSource>::getName).toSet(),
            "$name exposes exactly one tile argument",
        )
    }
}
