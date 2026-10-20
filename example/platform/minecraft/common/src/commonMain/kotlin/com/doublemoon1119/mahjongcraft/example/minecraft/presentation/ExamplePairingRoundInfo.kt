package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProviderDefinition

/**
 * 登記簡易配對麻將的局況文字與資料 provider。
 *
 * @param registry 要寫入局況顯示定義的 registry。
 */
fun registerExamplePairingRoundInfo(registry: RoundInfoLineDisplayRegistry) {
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
        RoundInfoLineProviderDefinition(
            ruleModuleId = ExamplePairingIds.RULE_MODULE,
            provider = RoundInfoLineProvider { tableState ->
                listOf(
                    RoundInfoLine(
                        key = ExampleMinecraftPresentationIds.WALL_REMAINING_INFO,
                        args = listOf(RoundInfoArgument.Number(tableState.tileWall.remainingCount)),
                    ),
                    RoundInfoLine(
                        key = ExampleMinecraftPresentationIds.ROUND_COUNT_INFO,
                        args = listOf(
                            RoundInfoArgument.Number(tableState.roundNumber),
                            RoundInfoArgument.Number((tableState.config as ExamplePairingRuleConfig).roundCount),
                        ),
                    ),
                )
            },
        ),
    )
}
