package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeMahjongClientExtension

/** 以各 mod 在 `mods.toml` 的 `modproperties` 宣告找出第三方擴充，屬性名稱與 Fabric 的 entrypoint 名稱相同。 */
internal object ForgeExtensionDiscovery {
    /** 第三方擴充宣告類別的屬性名稱。 */
    const val PROPERTY: String = "${MinecraftModMetadata.MOD_ID}:extension"

    /** 第三方客戶端擴充宣告類別的屬性名稱，不由專用伺服器讀取。 */
    const val CLIENT_PROPERTY: String = "${MinecraftModMetadata.MOD_ID}:client_extension"

    /**
     * 找出第三方擴充；同一個類別可同時實作各層擴充介面，只需宣告一次。
     *
     * @return 依 Forge 的 mod 順序排列的擴充。
     */
    fun discover(): List<MahjongExtension> = ForgeModPropertyClasses.instances(PROPERTY, MahjongExtension::class.java)

    /**
     * 找出第三方客戶端擴充。
     *
     * @return 依 Forge 的 mod 順序排列的客戶端擴充。
     */
    fun discoverClient(): List<NativeMahjongClientExtension> = ForgeModPropertyClasses.instances(CLIENT_PROPERTY, NativeMahjongClientExtension::class.java)
}
