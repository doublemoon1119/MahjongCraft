package com.doublemoon1119.mahjongcraft.flow.network.dto.registry

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewNetworkConverter
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ScoreConfigDto
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig

/**
 * 所有可擴充網路 DTO 的唯讀轉換。
 *
 * MahjongCraft 呼叫 [DtoDefinition] 的轉換函式時傳入，讓擴充的轉換函式能轉換巢狀的可擴充型別，
 * 例如規則設定中的計分設定；只能轉換，不能登記或凍結。
 */
interface NetworkDtoConverter {
    /** 組件網路值。 */
    val componentViews: ComponentViewNetworkConverter

    /** 規則配置。 */
    val ruleConfig: DtoConverter<MahjongRuleConfig, MahjongRuleConfigDto>

    /** 計分配置。 */
    val scoreConfig: DtoConverter<ScoreConfig, ScoreConfigDto>

    /** 遊戲長度。 */
    val gameLength: DtoConverter<GameLength, GameLengthDto>

    /** 流局原因。 */
    val exhaustiveDrawReason: DtoConverter<ExhaustiveDrawReason, ExhaustiveDrawReasonDto>

    /** 擴充遊戲動作。 */
    val extensionGameAction: DtoConverter<ExtensionGameAction, ExtensionGameActionDto>

    /** 擴充遊戲命令。 */
    val extensionGameCommand: DtoConverter<ExtensionGameCommand, ExtensionGameCommandDto>
}
