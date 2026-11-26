package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingBonusTilesPersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingHandRevealedPersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingPublicFacePersistenceDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingBonusTiles
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingComponents
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceDefinition

/**
 * 登記權威組件保存格式，與公開網路型別分開。
 *
 * @param registries 本次執行尚未凍結的保存登記。
 */
fun registerExampleComponentPersistenceDtos(registries: PersistenceRegistrars) {
    registries.components.register(
        definition = ComponentPersistenceDefinition(
            component = ExamplePairingComponents.BONUS_TILES,
            serializer = ExamplePairingBonusTilesPersistenceDto.serializer(),
            toDto = { ExamplePairingBonusTilesPersistenceDto(it.values.toSet()) },
            toDomain = { ExamplePairingBonusTiles(it.values.toSet()) },
        ),
    )
    registries.components.register(
        definition = ComponentPersistenceDefinition(
            component = ExamplePairingComponents.HAND_REVEALED,
            serializer = ExamplePairingHandRevealedPersistenceDto.serializer(),
            toDto = { ExamplePairingHandRevealedPersistenceDto },
            toDomain = {},
        ),
    )
    registries.components.register(
        definition = ComponentPersistenceDefinition(
            component = ExamplePairingComponents.PUBLIC_FACE,
            serializer = ExamplePairingPublicFacePersistenceDto.serializer(),
            toDto = { ExamplePairingPublicFacePersistenceDto },
            toDomain = {},
        ),
    )
}
