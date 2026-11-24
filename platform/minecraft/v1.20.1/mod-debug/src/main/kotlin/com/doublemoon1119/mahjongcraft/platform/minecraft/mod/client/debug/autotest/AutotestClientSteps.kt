package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

/** 用戶端自動測試的全部步驟，依執行順序；第一個步驟建立並進入測試世界。 */
internal val autotestClientSteps: List<AutotestStep> = buildList {
    add(AutotestEnterWorldStep)
    add(AutotestResourcesStep)
    add(AutotestItemsStep)
    add(AutotestScreensStep)
    add(AutotestPresentationsStep)
    add(AutotestTableStep)
    add(AutotestScenesStep)
    add(AutotestSaveStep)
    add(AutotestPlayStep)
    add(AutotestReplayStep)
}
