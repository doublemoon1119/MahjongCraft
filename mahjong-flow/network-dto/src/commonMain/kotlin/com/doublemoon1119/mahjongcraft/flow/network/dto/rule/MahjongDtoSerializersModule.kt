package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoRegistry
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlin.reflect.KClass

/**
 * 建立以 [registries] 為動態來源的 `SerializersModule`。模組本身可以早於 extension bootstrap
 * 建立；每次 polymorphic 編解碼都會查詢同一個 registry，因此後續完成的內建與第三方 DTO
 * 註冊會立即可見，不必重建 `Json`。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 使用指定註冊表解析多型 DTO 的序列化模組。
 */
fun buildMahjongDtoSerializersModule(registries: NetworkDtoRegistries): SerializersModule = SerializersModule {
    dynamicPolymorphic(MahjongRuleConfigDto::class, registries.ruleConfig)
    dynamicPolymorphic(ScoreConfigDto::class, registries.scoreConfig)
    dynamicPolymorphic(GameLengthDto::class, registries.gameLength)
    dynamicPolymorphic(DynamicRuleStateDto::class, registries.dynamicRuleState)
    dynamicPolymorphic(PlayerRuleStateDto::class, registries.playerRuleState)
    dynamicPolymorphic(DiscardPileDto::class, registries.discardPile)
    dynamicPolymorphic(ExhaustiveDrawReasonDto::class, registries.exhaustiveDrawReason)
    dynamicPolymorphic(ExtensionGameActionDto::class, registries.extensionGameAction)
    dynamicPolymorphic(ExtensionGameCommandDto::class, registries.extensionGameCommand)
}

/**
 * 建立會在每次編解碼時查詢 [registry] 的 polymorphic provider，而非在 [SerializersModule]
 * 建立當下複製靜態 subclass 清單。
 *
 * @param Dto 多型 DTO 基底型別。
 * @param baseClass 多型 DTO 基底類別。
 * @param registry 提供序列化器的動態註冊表。
 */
private fun <Dto : Any> SerializersModuleBuilder.dynamicPolymorphic(
    baseClass: KClass<Dto>,
    registry: DtoRegistry<*, Dto>,
) {
    polymorphicDefaultSerializer(baseClass) { dto -> registry.serializerFor(dto) }
    polymorphicDefaultDeserializer(baseClass) { serialName -> serialName?.let(registry::serializerFor) }
}
