package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents

/**
 * 將規則明確選擇公開的牌值轉成牌面與公開特徵，不建立牌張或產生 UUID。
 *
 * 呼叫端負責確認牌值可以公開；未知牌應走逐張可見性入口，不能傳到此處。
 * 投影使用公共讀取情境且沒有局內對象，不會取得依持有者授權的私有組件。
 *
 * @param ruleModuleId 選擇公開牌值的規則。
 * @param tableState 產生這份公開資料時的同一份桌況。
 * @param registry 已完成登記的特徵投影。
 * @return 不帶局內身分、授權名單或原始組件的公開牌面。
 */
fun TileValue.toPublicFaceView(
    ruleModuleId: String,
    tableState: TableState,
    registry: ComponentRegistry,
): TileFaceView = TileFaceView(
    tile = tile,
    views = registry.project(
        context = ComponentProjectionContext(
            ruleModuleId = ruleModuleId,
            tableState = tableState,
            ownerType = BuiltInComponentOwners.TILE,
            owner = null,
            reader = ComponentReadContext.Public,
        ),
        components = components,
    ),
    appearanceId = components[BuiltInTileComponents.APPEARANCE],
)
