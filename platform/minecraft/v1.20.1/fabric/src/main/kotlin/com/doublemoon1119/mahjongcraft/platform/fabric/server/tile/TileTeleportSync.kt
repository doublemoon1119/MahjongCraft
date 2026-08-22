package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement

/**
 * 把一張**已經生成、正在被 client 追蹤中**的管理中麻將牌立即（不經過動畫）移到 [placement]——手牌／
 * 摸牌位／副露／牌河等一般回合更新都在改既有牌的位置，不是生成新 entity，因此不能直接呼叫
 * `Entity.refreshPositionAndAngles`：那個方法只改真實座標，不會排定 `AnimatedMahjongEntity` 內建的
 * 延遲全量定位封包重播（見 [AnimatedMahjongEntity.resyncTrackerIfDue] KDoc 的完整說明），如果剛好有
 * client 在座標改變的極短暫窗口內開始追蹤這張牌，畫面會永久停在一個偏移過的錯誤位置，變成幽靈牌——
 * 這是遊戲內實際驗證過的問題，尤其在自動理牌一次同時搬動多張牌、或 AI 連續回合快速觸發多次呈現更新
 * 時特別容易踩到。
 *
 * 改成排一個 [AnimationStep.Teleport] 瞬間 step——[AnimatedMahjongEntity] 處理 `Teleport` 這個 step
 * 時本來就會呼叫 `refreshPositionAndAngles` 並排定那次延遲重播，兩者行為完全相容，只多了不到一個
 * tick 的排隊延遲（下一次這個 entity 自己的 `tick()` 才會真正套用），視覺上不可感知。
 */
internal fun MahjongTileEntity.teleportExistingManagedTile(placement: MahjongTileWallPlacement) {
    enqueue(AnimationStep.Teleport(placement.x, placement.y, placement.z, placement.yaw))
}
