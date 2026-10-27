package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 同一張桌子內的規則物件身分，不包含實體 UUID 或正式牌張 UUID。
 *
 * @property placementId 規則給定的穩定物件識別碼。
 * @property ruleModuleId 建立物件的規則模組識別碼。
 */
data class TablePropIdentity(
    val placementId: String,
    val ruleModuleId: String,
)
