package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundInfoLineDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoMessageDefinition

/**
 * 登記簡易配對麻將的局況文字與資料 provider。
 *
 * @param registry 要寫入局況顯示定義的 registry。
 */
fun registerExamplePairingRoundInfo(registry: RoundInfoLineDisplayRegistrar) {
    registry.register(
        RoundInfoLineDisplayDefinition(
            key = ExampleMinecraftPresentationIds.WALL_REMAINING_INFO,
            display = RoundInfoLineDisplay(ExampleMinecraftMessageKeys.ROUND_INFO_WALL_REMAINING),
        ),
    )
    registry.register(
        RoundInfoLineDisplayDefinition(
            key = ExampleMinecraftPresentationIds.ROUND_COUNT_INFO,
            display = RoundInfoLineDisplay(ExampleMinecraftMessageKeys.ROUND_INFO_ROUND_COUNT),
        ),
    )
    registry.register(
        RoundInfoLineDisplayDefinition(
            key = ExampleMinecraftPresentationIds.LATEST_DISCARD_INFO,
            display = RoundInfoLineDisplay(ExampleMinecraftMessageKeys.ROUND_INFO_LATEST_DISCARD),
        ),
    )
    registry.register(
        RoundInfoLineDisplayDefinition(
            key = ExampleMinecraftPresentationIds.EXCHANGE_STATUS_INFO,
            display = RoundInfoLineDisplay(ExampleMinecraftMessageKeys.ROUND_INFO_EXCHANGE),
        ),
    )
    registry.register(
        RoundInfoMessageDefinition(
            ExampleMinecraftPresentationIds.EXCHANGE_ENABLED_MESSAGE,
            ExampleMinecraftMessageKeys.ROUND_INFO_EXCHANGE_ENABLED,
        ),
    )
    registry.register(
        RoundInfoMessageDefinition(
            ExampleMinecraftPresentationIds.EXCHANGE_DISABLED_MESSAGE,
            ExampleMinecraftMessageKeys.ROUND_INFO_EXCHANGE_DISABLED,
        ),
    )
    registry.register(
        RoundInfoLineProviderDefinition(
            ruleModuleId = ExamplePairingIds.RULE_MODULE,
            provider = RoundInfoLineProvider { tableState ->
                val ruleConfig = tableState.config as ExamplePairingRuleConfig
                val latestDiscard = tableState.players.takeIf { it.isNotEmpty() }?.let { players ->
                    players[(tableState.currentPlayerIndex - 1).mod(players.size)]
                }?.discardPile?.entries?.lastOrNull()?.tile?.tile
                buildList {
                    add(
                        RoundInfoLine(
                            key = ExampleMinecraftPresentationIds.WALL_REMAINING_INFO,
                            args = listOf(RoundInfoArgument.Number(tableState.tileWall.remainingCount)),
                        ),
                    )
                    add(
                        RoundInfoLine(
                            key = ExampleMinecraftPresentationIds.ROUND_COUNT_INFO,
                            args = listOf(
                                RoundInfoArgument.Number(tableState.roundNumber),
                                RoundInfoArgument.Number(ruleConfig.roundCount),
                            ),
                        ),
                    )
                    latestDiscard?.let { tile ->
                        add(
                            RoundInfoLine(
                                key = ExampleMinecraftPresentationIds.LATEST_DISCARD_INFO,
                                args = listOf(RoundInfoArgument.TileValue(tile)),
                            ),
                        )
                    }
                    add(
                        RoundInfoLine(
                            key = ExampleMinecraftPresentationIds.EXCHANGE_STATUS_INFO,
                            args = listOf(
                                RoundInfoArgument.Message(
                                    if (ruleConfig.exchangeEnabled) {
                                        ExampleMinecraftPresentationIds.EXCHANGE_ENABLED_MESSAGE
                                    } else {
                                        ExampleMinecraftPresentationIds.EXCHANGE_DISABLED_MESSAGE
                                    },
                                ),
                            ),
                        ),
                    )
                }
            },
        ),
    )
}
