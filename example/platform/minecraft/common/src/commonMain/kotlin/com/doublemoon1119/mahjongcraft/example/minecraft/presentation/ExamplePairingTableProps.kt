package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingComponents
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingTileType
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriber
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout

/**
 * 描述簡易配對麻將本局中央展示的加分牌。
 *
 * 展示內容直接來自本局已保存的加分牌組件，不會重新抽選；加分牌面向本局莊家的實際席位，換莊後更新朝向。
 */
object ExamplePairingTableProps : TablePropDescriber {
    override fun describe(tableState: TableState): List<TablePropPlacement> {
        val bonusTiles = requireNotNull(tableState.components[ExamplePairingComponents.BONUS_TILES]) { "Example pairing bonus tiles are required" }
        val dealerYaw = MahjongTileTableLayout.seatYaw(
            seatLayout = MahjongTableSeatLayout.forPlayerCount(tableState.players.size),
            seatIndex = tableState.dealerIndex,
        )
        val bonusProps = bonusTiles.values
            .sorted()
            .mapIndexed { index, value ->
                TablePropPlacement(
                    id = "${ExamplePairingIds.RULE_MODULE}/bonus_tile_$value",
                    kind = BuiltInTablePropKinds.FACE_UP_TILE,
                    content = TablePropContent.FaceUpTile(ExamplePairingTileType.bonusFace(value)),
                    location = TablePropLocation.Center(order = index, yaw = dealerYaw),
                )
            }
        val handRevealProps = tableState.players.mapNotNull { player ->
            if (player.components[ExamplePairingComponents.HAND_REVEALED] == null) return@mapNotNull null
            TablePropPlacement(
                id = "${ExamplePairingIds.RULE_MODULE}/hand_reveal_chip_${player.id}",
                kind = ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_KIND,
                content = TablePropContent.CustomVariant(ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_VARIANT),
                location = TablePropLocation.Center(order = bonusTiles.values.size + player.initialSeatIndex),
            )
        }
        return bonusProps + handRevealProps
    }
}
