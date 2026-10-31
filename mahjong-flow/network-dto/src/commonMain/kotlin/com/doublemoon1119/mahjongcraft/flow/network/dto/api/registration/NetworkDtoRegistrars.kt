package com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
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

/** 擴充登記網路 DTO 時取得的各類登記介面；轉換與凍結由 MahjongCraft 進行。 */
interface NetworkDtoRegistrars {
    /** 已完成授權裁切的組件網路值。 */
    val componentViews: ComponentViewNetworkRegistrar

    /** 規則配置 DTO。 */
    val ruleConfig: DtoRegistrar<MahjongRuleConfig, MahjongRuleConfigDto>

    /** 計分配置 DTO。 */
    val scoreConfig: DtoRegistrar<ScoreConfig, ScoreConfigDto>

    /** 遊戲長度 DTO。 */
    val gameLength: DtoRegistrar<GameLength, GameLengthDto>

    /** 流局原因 DTO。 */
    val exhaustiveDrawReason: DtoRegistrar<ExhaustiveDrawReason, ExhaustiveDrawReasonDto>

    /** 擴充遊戲動作 DTO。 */
    val extensionGameAction: DtoRegistrar<ExtensionGameAction, ExtensionGameActionDto>

    /** 擴充遊戲命令 DTO。 */
    val extensionGameCommand: DtoRegistrar<ExtensionGameCommand, ExtensionGameCommandDto>
}
