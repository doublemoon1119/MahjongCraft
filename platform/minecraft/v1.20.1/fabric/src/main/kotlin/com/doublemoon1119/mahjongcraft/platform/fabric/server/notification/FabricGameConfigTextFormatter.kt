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
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import kotlinx.serialization.decodeFromString
import net.minecraft.text.MutableText
import net.minecraft.text.Text

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
    fun full(configJson: String): MutableText = runCatching { decode(configJson) }
        .fold(::full, { Text.literal(configJson) })

    /**
     * 只顯示實際改變的欄位；規則模組不同時只列出新舊規則名稱。
     *
     * @param oldConfigJson 變更前的遊戲設定 JSON。
     * @param newConfigJson 變更後的遊戲設定 JSON。
     * @return 本地化差異文字；解碼失敗時回傳新設定原始 JSON。
     */
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
                    .append(gameConfigPresentationText(field.read(old))).append(Text.literal(" → "))
                    .append(gameConfigPresentationText(field.read(new)))
            }
        }
    }.getOrElse { Text.literal(newConfigJson) }

    /**
     * 依已解析的設定欄位呈現完整本地化文字。
     *
     * @param config 要呈現的領域遊戲設定。
     * @return 完整設定文字。
     */
    private fun full(config: GameConfig): MutableText {
        val resolved = resolver.resolve(config)
        val moduleId = resolved.ruleModuleId
        val result = Text.empty().append(ruleName(moduleId))
        val definition = resolved.definition ?: return result.append("\n").append(Text.literal(moduleId))
        definition.categories.forEach { category ->
            result.append("\n\n").append(Text.translatable(category.nameTranslationKey))
            definition.fields.filter { it.categoryId == category.id }.forEach { field ->
                result.append("\n").append(Text.translatable(field.nameTranslationKey))
                    .append(Text.literal(": ")).append(gameConfigPresentationText(requireNotNull(resolved.valuesByFieldId[field.id])))
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
    private fun ruleName(moduleId: String): Text = ruleNames.find(moduleId)?.let(Text::translatable) ?: Text.literal(moduleId)

    /**
     * 將設定 JSON 解碼為領域設定。
     *
     * @param configJson 網路 DTO 格式的設定 JSON。
     * @return 轉換完成的領域遊戲設定。
     */
    private fun decode(configJson: String): GameConfig = json.decodeFromString<GameConfigDto>(configJson).toDomain(networkRegistries)
}
