package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.text

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewDefaults
import com.doublemoon1119.mahjongcraft.platform.fabric.text.buildMatchResultChatText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.buildRoundResultChatText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.configShowMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.serverConfigEntries
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.toKotlinUuid

/**
 * 建立 `/mahjongcraft debug hovered_text`：正式聊天訊息與 hover tooltip builder 的排版預覽。
 *
 * 這組子指令依「驗證哪一個 Text builder」分組，不是依預覽內容分組——三個結算節點直接呼叫
 * [buildRoundResultChatText] 與 [buildMatchResultChatText] 並餵入固定取樣資料，另外三個分別走開桌位置、
 * 對局設定與伺服器設定的正式訊息組裝路徑。全部只送出文字，不生成任何 entity、不排任何演出，也不改動
 * 任何權威狀態。
 *
 * @property feedbackPublisher 送出開桌位置與對局設定訊息。
 * @property serverConfigManager 提供伺服器設定的顯示路徑與目前內容。
 * @property json 序列化設定 DTO，供設定訊息比對變動項目。
 * @property networkRegistries 將設定轉成網路 DTO 時使用的 registry。
 */
@Single
class FabricDebugTextCommand(
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val serverConfigManager: FabricServerConfigManager,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 建立 `hovered_text` 的六種訊息排版預覽。 */
    fun buildHoveredTextCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(HOVERED_TEXT_SUBCOMMAND)
        .then(
            literal(EXHAUSTIVE_DRAW_SETTLEMENT_ARGUMENT)
                .executes { context -> previewExhaustiveDrawSettlementHoveredText(context.source) },
        )
        .then(
            literal(WIN_SETTLEMENT_ARGUMENT)
                .executes { context -> previewWinSettlementHoveredText(context.source) },
        )
        .then(
            literal(MATCH_SETTLEMENT_ARGUMENT)
                .executes { context -> previewMatchSettlementHoveredText(context.source) },
        )
        .then(literal(GAME_CREATED_LOCATION_ARGUMENT).executes { context -> previewGameCreatedLocationHoveredText(context.source) })
        .then(
            literal(GAME_CONFIG_ARGUMENT)
                .then(literal(SHOW_ARGUMENT).executes { context -> previewGameConfigHoveredText(context.source, SHOW_ARGUMENT) })
                .then(literal(CHANGED_ARGUMENT).executes { context -> previewGameConfigHoveredText(context.source, CHANGED_ARGUMENT) })
                .then(literal(UNCHANGED_ARGUMENT).executes { context -> previewGameConfigHoveredText(context.source, UNCHANGED_ARGUMENT) }),
        )
        .then(literal(SERVER_CONFIG_ARGUMENT).executes { context -> previewServerConfigHoveredText(context.source) })

    /** `hovered_text exhaustive_draw_settlement`：以正式 builder 發送一筆可懸停檢查的 round-result 訊息。 */
    private fun previewExhaustiveDrawSettlementHoveredText(source: ServerCommandSource): Int {
        val details = Text.empty()
        HOVERED_TEXT_SAMPLE_ROWS.forEachIndexed { index, row ->
            if (index > 0) details.append(Text.literal("\n"))
            details.append(
                Text.translatable(
                    MinecraftMessageKeys.ROUND_RESULT_PLAYER_LINE,
                    row.playerName,
                    row.previousRank.toString(),
                    row.currentRank.toString(),
                    row.rankSymbol,
                    row.previousScore.toString(),
                    row.currentScore.toString(),
                ),
            )
        }
        source.sendFeedback(
            {
                buildRoundResultChatText(
                    Text.translatable(MinecraftMessageKeys.GAME_ACTION_EXHAUSTIVE_DRAW),
                    details,
                )
            },
            false,
        )
        return COMMAND_SUCCESS
    }

    /** `hovered_text win_settlement`：以正式 round-result builder 預覽胡牌結算摘要。 */
    private fun previewWinSettlementHoveredText(source: ServerCommandSource): Int {
        val details = Text.empty()
            .append(Text.translatable(WinSettlementTranslationKeys.RON_SUMMARY, "Player", "AI 1"))
            .append(Text.literal("\n"))
            .append(Text.translatable(WinSettlementTranslationKeys.HAN_FU, "3", "30"))
            .append(Text.literal("\n"))
            .append(Text.translatable(WinSettlementTranslationKeys.TOTAL_SCORE, "7700"))
        source.sendFeedback(
            { buildRoundResultChatText(Text.translatable(WinSettlementTranslationKeys.RON), details) },
            false,
        )
        return COMMAND_SUCCESS
    }

    /** `hovered_text match_settlement`：以正式 match-result builder 預覽最終排行 hover。 */
    private fun previewMatchSettlementHoveredText(source: ServerCommandSource): Int {
        val details = Text.empty()
        listOf("PlayerLongName123" to "120000", "AI 1" to "45000", "PlayerC" to "25000", "PlayerD" to "-12000")
            .forEachIndexed { index, (name, score) ->
                if (index > 0) details.append(Text.literal("\n"))
                details.append(Text.translatable(MinecraftMessageKeys.RANKING_LINE, (index + 1).toString(), name, score))
            }
        source.sendFeedback({ buildMatchResultChatText(details) }, false)
        return COMMAND_SUCCESS
    }

    /** 使用正式 feedback publisher 發送建立牌桌位置的 hover 預覽。 */
    private fun previewGameCreatedLocationHoveredText(source: ServerCommandSource): Int {
        val player = source.player ?: return COMMAND_FAILURE
        feedbackPublisher.publish(
            player.uuid.toKotlinUuid(),
            MinecraftPlayerFeedback.GameCreated(
                TableLocation(
                    dimensionId = player.world.registryKey.value.toString(),
                    x = player.blockX,
                    y = player.blockY,
                    z = player.blockZ,
                ),
            ),
        )
        return COMMAND_SUCCESS
    }

    /** 使用正式 feedback publisher 發送遊戲設定 show／changed／unchanged hover 預覽。 */
    private fun previewGameConfigHoveredText(source: ServerCommandSource, variant: String): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val current = json.encodeToString(GameConfig(DebugPreviewDefaults.RULE_CONFIG).toDto(networkRegistries))
        val changed = json.encodeToString(
            GameConfig(DebugPreviewDefaults.RULE_CONFIG, GameFlowConfig(timeControl = ActionTimeControl.Short)).toDto(networkRegistries),
        )
        val feedback = when (variant) {
            CHANGED_ARGUMENT -> MinecraftPlayerFeedback.GameConfigChanged(current, changed)
            UNCHANGED_ARGUMENT -> MinecraftPlayerFeedback.GameConfigUnchanged(current)
            else -> MinecraftPlayerFeedback.ShowGameConfig(current)
        }
        feedbackPublisher.publish(player.uuid.toKotlinUuid(), feedback)
        return COMMAND_SUCCESS
    }

    /** 使用正式 config builder 發送目前 server config 的 hover 預覽。 */
    private fun previewServerConfigHoveredText(source: ServerCommandSource): Int {
        source.sendFeedback(
            {
                configShowMessage(
                    Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG),
                    serverConfigManager.displayPath,
                    serverConfigEntries(serverConfigManager.current),
                )
            },
            false,
        )
        return COMMAND_SUCCESS
    }

    /** 正式 round-result hover builder 的單列測試資料。 */
    private data class HoveredTextSampleRow(
        val playerName: String,
        val previousRank: Int,
        val currentRank: Int,
        val rankSymbol: String,
        val previousScore: Int,
        val currentScore: Int,
    )

    private companion object {
        /** `hovered_text` 子指令 literal。 */
        const val HOVERED_TEXT_SUBCOMMAND: String = "hovered_text"

        /** 流局結算 hover 文字 literal。 */
        const val EXHAUSTIVE_DRAW_SETTLEMENT_ARGUMENT: String = "exhaustive_draw_settlement"

        /** 胡牌結算 hover 文字 literal。 */
        const val WIN_SETTLEMENT_ARGUMENT: String = "win_settlement"

        /** 終局結算 hover 文字 literal。 */
        const val MATCH_SETTLEMENT_ARGUMENT: String = "match_settlement"

        /** 開桌位置 hover 文字 literal。 */
        const val GAME_CREATED_LOCATION_ARGUMENT: String = "game_created_location"

        /** 對局設定訊息 literal。 */
        const val GAME_CONFIG_ARGUMENT: String = "game_config"

        /** 伺服器設定訊息 literal。 */
        const val SERVER_CONFIG_ARGUMENT: String = "server_config"

        /** 顯示完整設定的 literal。 */
        const val SHOW_ARGUMENT: String = "show"

        /** 只顯示有變動項目的 literal。 */
        const val CHANGED_ARGUMENT: String = "changed"

        /** 顯示未變動狀態的 literal。 */
        const val UNCHANGED_ARGUMENT: String = "unchanged"

        /** 正式 round-result hover builder 的固定取樣資料。 */
        val HOVERED_TEXT_SAMPLE_ROWS: List<HoveredTextSampleRow> = listOf(
            HoveredTextSampleRow("Player", 4, 1, "↑", 16_000, 34_000),
            HoveredTextSampleRow("AI 1", 1, 2, "↓", 31_000, 25_000),
            HoveredTextSampleRow("AI 2", 2, 3, "↓", 28_000, 22_000),
            HoveredTextSampleRow("AI 3", 3, 4, "↓", 25_000, 19_000),
        )

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
