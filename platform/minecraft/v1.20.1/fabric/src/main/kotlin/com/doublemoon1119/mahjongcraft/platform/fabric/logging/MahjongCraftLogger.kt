package com.doublemoon1119.mahjongcraft.platform.fabric.logging

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * 建立以 `MahjongCraft/<類別名>` 命名的 logger，例如 `MahjongCraft/FabricHistoryOutboxWriter`。
 *
 * 名稱不含 `.`，因此只印出 logger 名稱最後一段的 log 格式（例如 Fabric 開發環境的 `%logger{1}`）也會顯示完整名稱，
 * 可以看出記錄來自本 mod 的哪個類別。
 *
 * @param owner 使用這個 logger 的類別。
 * @return 對應的 logger。
 */
fun mahjongCraftLogger(owner: KClass<*>): Logger = LoggerFactory.getLogger(
    "${MinecraftModMetadata.MOD_NAME}/${requireNotNull(owner.simpleName) { "Logger owner must be a named class" }}",
)
