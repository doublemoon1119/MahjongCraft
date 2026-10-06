package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.RULE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.SCORE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.categories
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.flowFields
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.readOnlyBoolean
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.readOnlyInt
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.translationKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText

/**
 * 台麻的 Minecraft 呈現整合，與平台無關的台麻 extension 使用同一個 extension ID。
 *
 * 包含花牌的貼圖、表情與標籤，以及規則名稱與設定畫面。
 */
object BundledTaiwanMinecraftExtension : MinecraftMahjongExtension {
    override val id: String = MahjongCraftMetadata.id("taiwan")

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
        registry.register(TaiwanTileTypes.SPRING, "flower_spring")
        registry.register(TaiwanTileTypes.SUMMER, "flower_summer")
        registry.register(TaiwanTileTypes.AUTUMN, "flower_autumn")
        registry.register(TaiwanTileTypes.WINTER, "flower_winter")
        registry.register(TaiwanTileTypes.PLUM, "flower_plum")
        registry.register(TaiwanTileTypes.ORCHID, "flower_orchid")
        registry.register(TaiwanTileTypes.BAMBOO, "flower_bamboo")
        registry.register(TaiwanTileTypes.CHRYSANTHEMUM, "flower_chrysanthemum")
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) {
        registry.register(BuiltInRuleModuleIds.TAIWAN, MinecraftMessageKeys.RULE_MODULE_TAIWAN)
    }

    override fun registerTileEmojis(registry: TileEmojiRegistry) {
        registry.register("flower_plum", "🀣")
        registry.register("flower_orchid", "🀤")
        registry.register("flower_bamboo", "🀥")
        registry.register("flower_chrysanthemum", "🀦")
        registry.register("flower_spring", "🀧")
        registry.register("flower_summer", "🀨")
        registry.register("flower_autumn", "🀩")
        registry.register("flower_winter", "🀪")
    }

    /**
     * 花牌成對出現：春夏秋冬右上角是紅色中文字、左上角是黑色數字（依春夏秋冬排序，1～4）；梅蘭菊竹右上角是
     * 紅色數字（依梅蘭菊竹排序，1～4，對應 [TaiwanTileTypes.ALL] 的順序）、左上角是黑色中文字。
     *
     * 花牌標籤一律標記 [TileLabel.forced]＝`true`：八張花牌都是同一個樹形剪影配不同季節配色（見
     * `mahjong_tile_flower_*.png`），彼此外觀相近、單靠材質不容易一眼分辨花色與順序，標籤本身兼有辨識
     * 功能，不只是給非中文圈玩家看的輔助資訊，因此無視玩家本機開關、永遠顯示。
     */
    override fun registerTileLabels(registry: TileLabelRegistry) {
        // 四季花牌：右上紅色中文字、左上黑色數字，依春夏秋冬排序
        registry.register("flower_spring", seasonalFlower(chinese = "春", order = "1"))
        registry.register("flower_summer", seasonalFlower(chinese = "夏", order = "2"))
        registry.register("flower_autumn", seasonalFlower(chinese = "秋", order = "3"))
        registry.register("flower_winter", seasonalFlower(chinese = "冬", order = "4"))

        // 四君子花牌：右上紅色數字、左上黑色中文字，依梅蘭菊竹排序
        registry.register("flower_plum", plantFlower(chinese = "梅", order = "1"))
        registry.register("flower_orchid", plantFlower(chinese = "蘭", order = "2"))
        registry.register("flower_chrysanthemum", plantFlower(chinese = "菊", order = "3"))
        registry.register("flower_bamboo", plantFlower(chinese = "竹", order = "4"))
    }

    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistry) {
        registry.register(taiwanGameConfigPresentation())
    }
}

/** 唯讀台麻設定畫面的 schema；台麻目前不能選用，只顯示固定的設定值。 */
private fun taiwanGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
    descriptionTranslationKey = translationKey("rule.taiwan.description"),
    selectable = false,
    unavailableReasonTranslationKey = translationKey("rule.taiwan.unavailable"),
    defaultRuleConfig = ::TaiwanRuleConfig,
    categories = categories(),
    fields = listOf(
        readOnlyBoolean("use_flower_tiles", RULE) { (it.ruleConfig as TaiwanRuleConfig).useFlowerTiles },
        readOnlyInt("minimum_win_constraint", RULE) { it.ruleConfig.minimumWinConstraint },
        readOnlyInt("initial_score", SCORE) { it.ruleConfig.scoreConfig.initialScore },
    ) + flowFields(),
)

/** 四季花牌：右上紅色中文字、左上黑色排序數字，永遠顯示（理由見 [BundledTaiwanMinecraftExtension.registerTileLabels]）。 */
private fun seasonalFlower(chinese: String, order: String): TileLabel = TileLabel(
    topLeft = TileLabelText(order, TileLabelColor.BLACK),
    topRight = TileLabelText(chinese, TileLabelColor.RED),
    forced = true,
)

/** 四君子花牌：右上紅色排序數字、左上黑色中文字，永遠顯示（理由見 [BundledTaiwanMinecraftExtension.registerTileLabels]）。 */
private fun plantFlower(chinese: String, order: String): TileLabel = TileLabel(
    topLeft = TileLabelText(chinese, TileLabelColor.BLACK),
    topRight = TileLabelText(order, TileLabelColor.RED),
    forced = true,
)
