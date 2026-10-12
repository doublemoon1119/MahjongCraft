package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import org.koin.core.annotation.Single

/** MahjongCraft runtime 目前使用的 network DTO registry 集合。 */
@Single(binds = [NetworkDtoRegistries::class])
class DefaultNetworkDtoRegistries : NetworkDtoRegistries {
    override val ruleConfig = DtoRegistry<MahjongRuleConfig, MahjongRuleConfigDto>()
    override val scoreConfig = DtoRegistry<ScoreConfig, ScoreConfigDto>()
    override val gameLength = DtoRegistry<GameLength, GameLengthDto>()
    override val dynamicRuleState = DtoRegistry<DynamicRuleState, DynamicRuleStateDto>()
    override val playerRuleState = DtoRegistry<PlayerRuleState, PlayerRuleStateDto>()
    override val discardPile = DtoRegistry<DiscardPile<*>, DiscardPileDto>()
    override val exhaustiveDrawReason = DtoRegistry<ExhaustiveDrawReason, ExhaustiveDrawReasonDto>()
    override val extensionGameAction = DtoRegistry<ExtensionGameAction, ExtensionGameActionDto>()
    override val extensionGameCommand = DtoRegistry<ExtensionGameCommand, ExtensionGameCommandDto>()

    override fun freeze() {
        ruleConfig.freeze()
        scoreConfig.freeze()
        gameLength.freeze()
        dynamicRuleState.freeze()
        playerRuleState.freeze()
        discardPile.freeze()
        exhaustiveDrawReason.freeze()
        extensionGameAction.freeze()
        extensionGameCommand.freeze()
    }
}
