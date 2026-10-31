package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewNetworkRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig

/** MahjongCraft runtime 目前使用的 network DTO registry 集合。 */
class DefaultNetworkDtoRegistries : NetworkDtoRegistries {
    override val componentViews = ComponentViewNetworkRegistry()
    override val ruleConfig = DtoRegistry<MahjongRuleConfig, MahjongRuleConfigDto> { this }
    override val scoreConfig = DtoRegistry<ScoreConfig, ScoreConfigDto> { this }
    override val gameLength = DtoRegistry<GameLength, GameLengthDto> { this }
    override val exhaustiveDrawReason = DtoRegistry<ExhaustiveDrawReason, ExhaustiveDrawReasonDto> { this }
    override val extensionGameAction = DtoRegistry<ExtensionGameAction, ExtensionGameActionDto> { this }
    override val extensionGameCommand = DtoRegistry<ExtensionGameCommand, ExtensionGameCommandDto> { this }

    override fun freeze() {
        componentViews.freeze()
        ruleConfig.freeze()
        scoreConfig.freeze()
        gameLength.freeze()
        exhaustiveDrawReason.freeze()
        extensionGameAction.freeze()
        extensionGameCommand.freeze()
    }
}
