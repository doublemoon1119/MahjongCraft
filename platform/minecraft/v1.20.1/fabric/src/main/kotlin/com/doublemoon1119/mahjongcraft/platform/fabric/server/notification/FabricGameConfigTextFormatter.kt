package com.doublemoon1119.mahjongcraft.platform.fabric.server.notification

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.text.gameConfigPresentationText
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigVariantResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import kotlinx.serialization.decodeFromString
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * 將權威 GameConfig 依宣告式 schema 格式化成 GUI 與 hover 共用的本地化文字。
 *
 * @property presentations 各規則模組的設定欄位呈現定義。
 * @property ruleNames 規則模組顯示名稱註冊表。
 * @property modules 依規則設定解析所屬模組。
 * @property resolver 將設定解析為模組與欄位值。
 * @property json 解碼網路設定 DTO 的 JSON 格式器。
 * @property networkRegistries 將網路設定 DTO 轉換為領域設定所需的註冊表。
 */
class FabricGameConfigTextFormatter(
    private val presentations: GameConfigPresentationRegistry,
    private val ruleNames: RuleModuleDisplayNameRegistry,
    private val modules: MahjongModuleRegistry,
    private val resolver: GameConfigPresentationResolver,
    private val json: NetworkJson,
    private val networkRegistries: NetworkDtoRegistries,
) {
    /**
     * 顯示完整設定；無法解碼時安全退回原始 JSON，無 schema 時列出規則 ID。
     *
     * @param configJson 網路格式的遊戲設定 JSON。
     * @return 本地化的完整設定文字。
     */
    fun full(configJson: String): MutableComponent = runCatching { decode(configJson) }
        .fold(::full, { Component.literal(configJson) })

    /**
     * 只顯示實際改變的欄位；規則模組不同時只列出新舊規則名稱。
     *
     * @param oldConfigJson 變更前的遊戲設定 JSON。
     * @param newConfigJson 變更後的遊戲設定 JSON。
     * @return 本地化差異文字；解碼失敗時回傳新設定原始 JSON。
     */
    fun changes(oldConfigJson: String, newConfigJson: String): MutableComponent = runCatching {
        val old = decode(oldConfigJson)
        val new = decode(newConfigJson)
        val oldModuleId = modules.getModule(old.ruleConfig).id
        val newModuleId = modules.getModule(new.ruleConfig).id
        if (oldModuleId != newModuleId) {
            return@runCatching Component.empty()
                .append(ruleName(oldModuleId)).append(Component.literal(" → ")).append(ruleName(newModuleId))
        }
        val definition = presentations.find(newModuleId, new.ruleConfig) ?: return@runCatching full(new)
        val oldDefinition = presentations.find(oldModuleId, old.ruleConfig) ?: return@runCatching full(new)
        val oldFieldsById = oldDefinition.fields.associateBy { it.id }
        if (definition.fields.any { it.id !in oldFieldsById } || oldDefinition.fields.any { it.id !in definition.fields.map { field -> field.id } }) {
            return@runCatching full(new)
        }
        val changed = GameConfigVariantResolver.visibleFields(definition).filter { field ->
            val oldField = oldFieldsById.getValue(field.id)
            oldField.read(old) != field.read(new)
        }
        val oldVariant = GameConfigVariantResolver.find(oldDefinition, old)
        val newVariant = GameConfigVariantResolver.find(definition, new)
        val variantChange = if (oldVariant != null && newVariant != null && oldVariant.id != newVariant.id) oldVariant to newVariant else null
        if (changed.isEmpty() && variantChange == null) return@runCatching full(new)
        Component.empty().also { result ->
            variantChange?.let { (from, to) ->
                result.append(Component.translatable(MinecraftRoomScreenKeys.VARIANT_SELECTOR)).append(Component.literal(": "))
                    .append(Component.translatable(from.nameTranslationKey)).append(Component.literal(" → "))
                    .append(Component.translatable(to.nameTranslationKey))
            }
            changed.forEachIndexed { index, field ->
                if (index > 0 || variantChange != null) result.append("\n")
                val oldField = oldFieldsById.getValue(field.id)
                result.append(Component.translatable(field.nameTranslationKey)).append(Component.literal(": "))
                    .append(gameConfigPresentationText(oldField.read(old), oldField.editor)).append(Component.literal(" → "))
                    .append(gameConfigPresentationText(field.read(new), field.editor))
            }
        }
    }.getOrElse { Component.literal(newConfigJson) }

    /**
     * 依已解析的設定欄位呈現完整本地化文字。
     *
     * @param config 要呈現的領域遊戲設定。
     * @return 完整設定文字。
     */
    private fun full(config: GameConfig): MutableComponent {
        val resolved = resolver.resolve(config)
        val moduleId = resolved.ruleModuleId
        val result = Component.empty().append(ruleName(moduleId))
        val definition = resolved.definition ?: return result.append("\n").append(Component.literal(moduleId))
        resolved.variant?.takeIf { definition.variants.isNotEmpty() }?.let { variant ->
            result.append("\n").append(Component.translatable(MinecraftRoomScreenKeys.VARIANT_SELECTOR)).append(Component.literal(": "))
                .append(Component.translatable(variant.nameTranslationKey))
        }
        val fields = GameConfigVariantResolver.visibleFields(definition)
        definition.categories.forEach { category ->
            result.append("\n\n").append(Component.translatable(category.nameTranslationKey))
            fields.filter { it.categoryId == category.id }.forEach { field ->
                result.append("\n").append(Component.translatable(field.nameTranslationKey))
                    .append(Component.literal(": ")).append(
                        gameConfigPresentationText(
                            value = requireNotNull(resolved.valuesByFieldId[field.id]),
                            editor = field.editor,
                        ),
                    )
            }
        }
        return result
    }

    /**
     * 解析規則模組顯示名稱，缺少翻譯時退回模組 ID。
     *
     * @param moduleId 規則模組 ID。
     * @return 可本地化的名稱文字。
     */
    private fun ruleName(moduleId: String): Component = ruleNames.find(moduleId)?.let(Component::translatable) ?: Component.literal(moduleId)

    /**
     * 將設定 JSON 解碼為領域設定。
     *
     * @param configJson 網路 DTO 格式的設定 JSON。
     * @return 轉換完成的領域遊戲設定。
     */
    private fun decode(configJson: String): GameConfig = json.decodeFromString<GameConfigDto>(configJson).toDomain(networkRegistries)
}
