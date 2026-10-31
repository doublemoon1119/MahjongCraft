package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftRuleTileAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileStyleAssetDefinition

/** 擴充登記牌張模型、材質與外觀時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface MinecraftTileAssetRegistrar {
    /** 目前已登記 asset key 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後、身分重複或素材 key 與基本牌／未知牌重名時拒絕。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: MinecraftTileAssetDefinition)

    /**
     * 登記外觀樣式套在指定基本牌面時使用的素材；凍結後、同一組樣式與牌面重複，或素材 key 已被使用時拒絕。
     *
     * @param definition 外觀樣式、基本牌面與素材的對應。
     */
    fun register(definition: MinecraftTileStyleAssetDefinition)

    /**
     * 登記一套規則中立的牌組共用外觀。
     *
     * @param definition 要登記的牌組外觀描述。
     */
    fun register(definition: MinecraftTileSetAppearanceDefinition)

    /**
     * 登記規則使用的預設牌組外觀。
     *
     * @param definition 要登記的規則外觀綁定。
     */
    fun register(definition: MinecraftRuleTileAppearanceDefinition)
}
