package com.doublemoon1119.mahjongcraft.platform.fabric.server.notification

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/** 將權威 GameConfig 依宣告式 schema 格式化成 GUI 與 hover 共用的本地化文字。 */
@Single
class FabricGameConfigTextFormatter(
    @Provided private val presentations: GameConfigPresentationRegistry,
    @Provided private val ruleNames: RuleModuleDisplayNameRegistry,
    @Provided private val modules: MahjongModuleRegistry,
    @Provided private val resolver: GameConfigPresentationResolver,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 顯示完整設定；無 schema 時安全退回規則 ID。 */
    fun full(configJson: String): MutableText = runCatching { decode(configJson) }
        .fold(::full, { Text.literal(configJson) })

    /** 只顯示實際改變的欄位。 */
    fun changes(oldConfigJson: String, newConfigJson: String): MutableText = runCatching {
        val old = decode(oldConfigJson)
        val new = decode(newConfigJson)
        val oldModuleId = modules.getModule(old.ruleConfig).id
        val newModuleId = modules.getModule(new.ruleConfig).id
        if (oldModuleId != newModuleId) {
            return@runCatching Text.empty()
                .append(ruleName(oldModuleId)).append(Text.literal(" → ")).append(ruleName(newModuleId))
        }
        val definition = presentations.find(newModuleId) ?: return@runCatching full(new)
        val changed = definition.fields.filter { field -> field.read(old) != field.read(new) }
        if (changed.isEmpty()) return@runCatching full(new)
        Text.empty().also { result ->
            changed.forEachIndexed { index, field ->
                if (index > 0) result.append("\n")
                result.append(Text.translatable(field.nameTranslationKey)).append(Text.literal(": "))
                    .append(valueText(field.read(old))).append(Text.literal(" → ")).append(valueText(field.read(new)))
            }
        }
    }.getOrElse { Text.literal(newConfigJson) }

    private fun full(config: GameConfig): MutableText {
        val resolved = resolver.resolve(config)
        val moduleId = resolved.ruleModuleId
        val result = Text.empty().append(ruleName(moduleId))
        val definition = resolved.definition ?: return result.append("\n").append(Text.literal(moduleId))
        definition.categories.forEach { category ->
            result.append("\n\n").append(Text.translatable(category.nameTranslationKey))
            definition.fields.filter { it.categoryId == category.id }.forEach { field ->
                result.append("\n").append(Text.translatable(field.nameTranslationKey))
                    .append(Text.literal(": ")).append(valueText(requireNotNull(resolved.valuesByFieldId[field.id])))
            }
        }
        return result
    }

    private fun valueText(value: GameConfigPresentationValue): Text = when (value) {
        is GameConfigPresentationValue.BooleanValue -> Text.translatable(
            if (value.enabled) MinecraftRoomScreenKeys.TRUE else MinecraftRoomScreenKeys.FALSE,
        )
        is GameConfigPresentationValue.IntegerValue -> value.number?.let { Text.literal(it.toString()) }
            ?: Text.translatable(MinecraftRoomScreenKeys.NONE)
        is GameConfigPresentationValue.ChoiceValue ->
            Text.translatable("mahjongcraft.room.config.option.${value.optionId.substringAfter(':')}")
    }

    private fun ruleName(moduleId: String): Text = ruleNames.find(moduleId)?.let(Text::translatable) ?: Text.literal(moduleId)

    private fun decode(configJson: String): GameConfig = json.decodeFromString<GameConfigDto>(configJson).toDomain(networkRegistries)
}
