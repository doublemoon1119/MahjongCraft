package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.text.gameConfigPresentationText
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 建立房間設定頁與未套用變更確認畫面共用的本地化差異清單。
 *
 * 規則不同時只列規則變更；相同時只列兩邊都有值且內容不同的已登記欄位。
 *
 * @param resolver 將設定解析成可顯示欄位的解析器。
 * @param ruleNames 規則顯示名稱登錄表。
 * @param from 變更前的設定。
 * @param to 變更後的設定。
 * @return 以標題開頭的多行差異文字；沒有可列出的差異時只有標題。
 */
fun gameConfigDifferenceText(
    resolver: GameConfigPresentationResolver,
    ruleNames: RuleModuleDisplayNameRegistry,
    from: GameConfig,
    to: GameConfig,
): Text {
    val fromResolved = resolver.resolve(from)
    val toResolved = resolver.resolve(to)
    val result = Text.translatable(MinecraftRoomScreenKeys.CONFIG_CHANGES).formatted(Formatting.GOLD)
    if (fromResolved.ruleModuleId != toResolved.ruleModuleId) {
        result.appendValueChange(
            MinecraftRoomScreenKeys.RULE,
            ruleNameText(ruleNames, fromResolved.ruleModuleId),
            ruleNameText(ruleNames, toResolved.ruleModuleId),
        )
        return result
    }
    val definition = toResolved.definition ?: return result
    definition.fields.forEach { field ->
        val fromValue = fromResolved.valuesByFieldId[field.id] ?: return@forEach
        val toValue = toResolved.valuesByFieldId[field.id] ?: return@forEach
        if (fromValue != toValue) {
            result.appendValueChange(
                field.nameTranslationKey,
                gameConfigPresentationText(fromValue),
                gameConfigPresentationText(toValue),
            )
        }
    }
    return result
}

/**
 * 取得規則模組的顯示名稱；未登記時退回模組 ID 本身。
 *
 * @param ruleNames 規則顯示名稱登錄表。
 * @param moduleId 規則模組識別碼。
 * @return 可翻譯名稱或原始識別碼。
 */
private fun ruleNameText(ruleNames: RuleModuleDisplayNameRegistry, moduleId: String): Text = ruleNames.find(moduleId)?.let(Text::translatable) ?: Text.literal(moduleId)

/**
 * 附加一列設定值差異。
 *
 * @param nameKey 設定名稱的翻譯鍵。
 * @param from 變更前的值。
 * @param to 變更後的值。
 */
private fun MutableText.appendValueChange(nameKey: String, from: Text, to: Text) {
    append("\n").append(
        Text.translatable(
            MinecraftRoomScreenKeys.CONFIG_VALUE_CHANGE,
            Text.translatable(nameKey),
            from,
            to,
        ).formatted(Formatting.GRAY),
    )
}
