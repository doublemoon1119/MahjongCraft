package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSelectionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.ClientDecisionPromptStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * 伺服器給本機玩家的一個可選動作，以及它所屬的操作提示。
 *
 * @property prompt 動作所屬的操作提示。
 * @property action 動作。
 */
internal data class AutotestDecision(val prompt: PlayerDecisionPromptDto, val action: PlayerDecisionActionDto)

/** 本機玩家目前的操作提示；沒有等玩家操作時為 null。 */
internal val AutotestContext.currentPrompt: PlayerDecisionPromptDto?
    get() = GlobalContext.get().get<ClientDecisionPromptStore>().prompt

/**
 * 目前操作提示中可選的動作，供檢查失敗時說明。
 *
 * @return 動作識別碼，以逗號分隔；沒有操作提示時說明沒有。
 */
internal fun AutotestContext.offeredActions(): String = currentPrompt?.actions?.joinToString { it.actionId } ?: "No decision prompt"

/**
 * 等到伺服器給本機玩家指定的動作。
 *
 * @param actionId 動作識別碼，例如 `mahjongcraft:riichi/declare_riichi`。
 * @return 動作與它的操作提示；時限內沒有出現時為 null。
 */
internal suspend fun AutotestContext.waitForAction(actionId: String): AutotestDecision? {
    var decision: AutotestDecision? = null
    waitUntil(DECISION_TIMEOUT) {
        val prompt = currentPrompt
        decision = prompt?.actions?.firstOrNull { it.actionId == actionId }?.let { AutotestDecision(prompt, it) }
        decision != null
    }
    return decision
}

/**
 * 以操作介面相同的方式送出動作，並等伺服器換到下一個操作提示，表示已經接受。
 *
 * 動作需要選牌時，選第一張可選的牌；只有一種選法的情境（例如立直時只有一張牌打出後仍聽牌）結果是確定的。
 *
 * @param decision 要送出的動作。
 * @return 伺服器在時限內接受時為 true。
 */
internal suspend fun AutotestContext.submit(decision: AutotestDecision): Boolean {
    val koin = GlobalContext.get()
    val gameId = koin.get<ClientDecisionPromptStore>().gameId ?: return false
    val selection = decision.action.tileSelection
    val tileIds = selection?.eligibleTileIds?.take(selection.minCount.coerceAtLeast(1)).orEmpty()
    MahjongChannels.decisionSelection.sendToServer(
        json = koin.get<NetworkJson>(),
        value = PlayerDecisionSelectionDto(
            gameId = gameId.toString(),
            decisionKey = decision.prompt.decisionKey,
            kind = PlayerDecisionSelectionKindDto.ACTION,
            token = decision.action.token,
            tileIds = tileIds,
            submissionId = Uuid.random().toString(),
        ),
    )
    return waitUntil(DECISION_TIMEOUT) { currentPrompt?.decisionKey != decision.prompt.decisionKey }
}

/** 等伺服器給動作或接受動作的最長時間。 */
private val DECISION_TIMEOUT = 2.minutes
