package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterPreviewHandle
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.AiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.TablePropPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.TablePropPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.TablePropTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.world.entity.Entity

/**
 * 內建角色與桌上物件也透過客戶端公開登記入口建立預覽。
 *
 * @property tileAssets 明牌物件選擇牌面素材與規則外觀的來源，與對局中生成的明牌相同。
 */
internal class BuiltInNativeMahjongClientExtension(
    private val tileAssets: MinecraftTileAssetRegistry,
) : NativeMahjongClientExtension {
    override val id: String = "mahjongcraft:built_in_client"

    override fun registerCharacterPreviews(registry: AiCharacterPreviewRegistrar) {
        registry.register(
            definition = AiCharacterPreviewDefinition(BuiltInAiCharacters.TILE_MASCOT_CAPABILITY_ID) { world, character ->
                val model = MahjongBotEntity(world = world)
                model.faceAssetKey = (character.face as? AiCharacterFace.Tile)?.assetKey ?: model.faceAssetKey
                object : AiCharacterPreviewHandle {
                    override val entity: Entity = model

                    override fun close() {
                        model.discard()
                    }
                }
            },
        )
    }

    override fun registerTablePropPreviews(registry: TablePropPreviewRegistrar) {
        registry.register(
            definition = TablePropPreviewDefinition(BuiltInTablePropDefinitions.SCORING_STICK) { world, target ->
                val value = (target.content as? TablePropContent.CustomVariant)?.value
                val denomination = MahjongScoringStickDenomination.entries.firstOrNull { it.points.toString() == value }
                denomination?.let { MahjongScoringStickEntity(world = world).apply { this.denomination = it } }
            },
        )
        registry.register(
            definition = TablePropPreviewDefinition(BuiltInTablePropDefinitions.FACE_UP_TILE) { world, target ->
                val content = target.content as? TablePropContent.FaceUpTile
                val appearance = tileAssets.findAppearanceForRule(target.ruleModuleId)
                if (content == null || appearance == null) {
                    null
                } else {
                    TablePropTileEntity(world = world).apply {
                        assetKey = content.face.toAssetKey(tileAssets)
                        appearanceId = appearance.id
                    }
                }
            },
        )
    }
}
