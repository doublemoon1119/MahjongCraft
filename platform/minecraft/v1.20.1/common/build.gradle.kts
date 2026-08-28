// 版本特定但跨 loader 共用的資源／程式碼。`data/mahjongcraft/recipes` 放在這裡，不放在真正跨版本
// 共用的 :minecraft_common——recipe JSON 的 `result` 欄位格式會隨 Minecraft 版本破版（1.20.1 用
// `item`，1.21+ 改用 `id`），日後新增版本時若也需要 recipe，在該版本自己的模組另外維護一份，不要
// 嘗試共用同一份跨版本。模型／材質／語言檔目前沒有證據顯示會隨版本破版，維持放在 :minecraft_common。

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.version.common)
}

version = libs.versions.minecraft.mod.version.get()

kotlin {
    jvm()
}
