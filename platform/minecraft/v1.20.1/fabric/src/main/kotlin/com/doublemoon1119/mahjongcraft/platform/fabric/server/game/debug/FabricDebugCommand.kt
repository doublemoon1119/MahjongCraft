package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.animation.FabricDebugAnimationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.FabricDebugDecisionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.FabricDebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugProgressionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugRoundCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.FabricDebugScenarioCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.text.FabricDebugTextCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import org.koin.core.annotation.Single

/**
 * 註冊 development-only、op 限定的 `/mahjongcraft debug ...` 指令根節點與各功能測試子指令。
 *
 * 各子指令樹由專責的 family command 建立，本類別只把它們回傳的節點掛在既有位置，不認識那些 handler：
 *
 * - `scenario`：[FabricDebugScenarioCommand]
 * - `dice`、`deal`、`draw`、`discard`、`meld`：[FabricDebugAnimationCommand]
 * - `win`、`showcase`、三種 settlement、`continuing_win`、`win_showcase_override`：
 *   [FabricDebugPresentationCommand]
 * - `hovered_text`：[FabricDebugTextCommand]
 * - `decision_hud`、`preparation`：[FabricDebugDecisionCommand]
 * - `match_progression`：[FabricDebugProgressionCommand]
 * - `round`：[FabricDebugRoundCommand]
 *
 * 本類別只保留三項責任：development gating、op 權限，以及各 family 共用的臨時 entity 到期清除驅動
 * （[DebugPreviewEntityLifecycle] 的 tick 登記只在這裡做一次）。
 *
 * 整組指令樹只在 [MinecraftEnvironment.isDevelopment] 為 `true` 時才註冊——正式打包發布的產物裡整棵
 * debug 指令樹根本沒被註冊過，不是「有指令但用權限藏起來」而已。額外再疊一層 op 權限
 * （`hasPermissionLevel(2)`）當作雙重保險，避免「不小心用非開發建置跑本地測試伺服器」時被一般玩家
 * 誤用。
 *
 * @property minecraftEnvironment 查詢目前是否為開發環境，決定整組指令樹要不要註冊。
 * @property scenarioCommand 建立權威對局情境子指令樹。
 * @property animationCommand 建立自由 entity 動畫子指令樹。
 * @property presentationCommand 建立對局演出預覽子指令樹。
 * @property textCommand 建立訊息排版預覽子指令樹。
 * @property decisionCommand 建立玩家決策互動預覽子指令樹。
 * @property progressionCommand 建立終局推進預覽子指令樹。
 * @property roundCommand 建立換局推進子指令樹。
 * @property entityLifecycle 保管並驅動臨時 entity 的到期清除。
 */
@Single
class FabricDebugCommand(
    private val minecraftEnvironment: MinecraftEnvironment,
    private val scenarioCommand: FabricDebugScenarioCommand,
    private val animationCommand: FabricDebugAnimationCommand,
    private val presentationCommand: FabricDebugPresentationCommand,
    private val textCommand: FabricDebugTextCommand,
    private val decisionCommand: FabricDebugDecisionCommand,
    private val progressionCommand: FabricDebugProgressionCommand,
    private val roundCommand: FabricDebugRoundCommand,
    private val entityLifecycle: DebugPreviewEntityLifecycle,
) {
    /** 註冊整組 debug 指令樹；只有開發環境才真的呼叫 `dispatcher.register`，見類別 KDoc。 */
    fun register() {
        if (!minecraftEnvironment.isDevelopment) return
        entityLifecycle.registerTicking()
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(build())
        }
    }

    /** 建立保留既有 literal、權限與子指令順序的完整 debug 指令樹。 */
    internal fun build(): LiteralArgumentBuilder<ServerCommandSource> = literal(MinecraftModMetadata.MOD_ID).then(
        literal(DEBUG_SUBCOMMAND)
            .requires { it.hasPermissionLevel(OP_PERMISSION_LEVEL) }
            .then(scenarioCommand.build())
            .then(presentationCommand.buildWinCommand())
            .then(presentationCommand.buildShowcaseCommand())
            .then(animationCommand.buildDiceCommand())
            .then(animationCommand.buildDealCommand())
            .then(animationCommand.buildDrawCommand())
            .then(animationCommand.buildDiscardCommand())
            .then(presentationCommand.buildExhaustiveDrawSettlementCommand())
            .then(textCommand.buildHoveredTextCommand())
            .then(presentationCommand.buildWinSettlementCommand())
            .then(presentationCommand.buildMatchSettlementCommand())
            .then(progressionCommand.buildMatchProgressionCommand())
            .then(decisionCommand.buildDecisionHudCommand())
            .then(animationCommand.buildMeldCommand())
            .then(presentationCommand.buildContinuingWinCommand())
            .then(presentationCommand.buildWinShowcaseOverrideCommand())
            .then(decisionCommand.buildPreparationCommand())
            .then(roundCommand.buildRoundCommand()),
    )

    private companion object {
        /** debug 指令樹要求的最低權限等級。 */
        const val OP_PERMISSION_LEVEL: Int = 2

        /** debug 指令樹的根 literal。 */
        const val DEBUG_SUBCOMMAND: String = "debug"
    }
}
