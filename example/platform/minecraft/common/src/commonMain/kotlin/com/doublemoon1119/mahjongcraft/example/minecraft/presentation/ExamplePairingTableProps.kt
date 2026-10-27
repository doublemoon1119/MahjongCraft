package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDynamicState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingTileType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
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
 * 展示內容直接來自本局已保存的動態狀態，不會重新抽選；加分牌面向本局莊家的實際席位，換莊後更新朝向。
 */
object ExamplePairingTableProps : TablePropDescriber {
    override fun describe(tableState: TableState): List<TablePropPlacement> {
        val state = tableState.dynamicRuleState as? ExamplePairingDynamicState ?: return emptyList()
        val dealerYaw = MahjongTileTableLayout.seatYaw(
            seatLayout = MahjongTableSeatLayout.forPlayerCount(tableState.players.size),
            seatIndex = tableState.dealerIndex,
        )
        val bonusProps = state.bonusTileValues
            .sorted()
            .mapIndexed { index, value ->
                TablePropPlacement(
                    id = "${ExamplePairingIds.RULE_MODULE}/bonus_tile_$value",
                    kind = BuiltInTablePropKinds.FACE_UP_TILE,
                    content = TablePropContent.FaceUpTile(value.toPhysicalTile()),
                    location = TablePropLocation.Center(order = index, yaw = dealerYaw),
                )
            }
        val declarationProps = tableState.players.mapNotNull { player ->
            val declared = player.playerRuleState as? ExamplePairingPlayerState
            if (declared?.declared != true) return@mapNotNull null
            TablePropPlacement(
                id = "${ExamplePairingIds.RULE_MODULE}/declaration_chip_${player.id}",
                kind = ExampleMinecraftPresentationIds.DECLARATION_CHIP_KIND,
                content = TablePropContent.CustomVariant(ExampleMinecraftPresentationIds.DECLARATION_CHIP_VARIANT),
                location = TablePropLocation.Center(order = state.bonusTileValues.size + player.initialSeatIndex),
            )
        }
        return bonusProps + declarationProps
    }

    /** 將設定中的牌面數值轉成桌面上要展示的實體牌面。 */
    private fun Int.toPhysicalTile(): Tile = if (this == 1) {
        ExamplePairingTileType.invertedOneMan()
    } else {
        Tile.Numeric(Tile.Suit.Character, this)
    }
}
