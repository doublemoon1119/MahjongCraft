package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import net.minecraft.advancements.CriterionTrigger
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation

/**
 * 登記一個進度觸發器，讓進度檔可以用 [id] 引用它。
 *
 * @param T 觸發器型別。
 * @param registrar 寫入登記表的方式。
 * @param id 觸發器的識別碼。
 * @param factory 建立觸發器的函式。
 * @param onRegistered 全部項目寫入登記表後帶著觸發器呼叫。
 */
fun <T : CriterionTrigger<*>> registerCriterion(registrar: ModRegistrar, id: ResourceLocation, factory: () -> T, onRegistered: (T) -> Unit) {
    val entry = registrar.register(registry = Registries.TRIGGER_TYPE, id = id, factory = factory)
    registrar.afterRegistration { onRegistered(entry.get()) }
}
