package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 僅允許正版帳號使用的隨機 UUID 進入原生皮膚查詢，不查詢 offline-mode 的名稱衍生 UUID。
 *
 * @param playerId 欲解析皮膚的真人識別碼。
 * @return 是否具有正版帳號 UUID 的版本；未知版本安全使用預設外觀。
 */
internal fun supportsNativePlayerSkinLookup(playerId: Uuid): Boolean = playerId.toJavaUuid().version() == ONLINE_UUID_VERSION

/** 正版帳號採用的 UUID 版本，offline-mode 使用名稱衍生的版本 3。 */
private const val ONLINE_UUID_VERSION = 4
