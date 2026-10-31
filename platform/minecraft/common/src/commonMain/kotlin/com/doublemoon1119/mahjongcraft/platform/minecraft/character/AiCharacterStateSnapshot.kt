package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import kotlin.uuid.Uuid

/**
 * 同一次發布的牌局與角色保存封套。
 *
 * @property authoritative 這次提交的權威牌局。
 * @property characters 同一次擷取的角色資料；無法讀取時為 null，禁止修改選擇。
 * @property sessionId 這次載入的 session 身分。
 * @property revision 這次 session 內單調增加的版本。
 */
data class AiCharacterStateSnapshot(
    val authoritative: AuthoritativeStateSnapshot,
    val characters: AiCharacterMetadata?,
    val sessionId: Uuid,
    val revision: Long,
)
