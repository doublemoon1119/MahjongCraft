package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import net.minecraft.advancements.CriteriaTriggers
import net.minecraft.advancements.CriterionTrigger
import net.minecraft.resources.ResourceLocation

/**
 * 登記一個進度觸發器，讓進度檔可以用 [id] 引用它。
 *
 * 這個版本的觸發器寫入原版共用的觸發器表，因此在登記完成後才建立並寫入。
 *
 * @param T 觸發器型別。
 * @param registrar 寫入登記表的方式。
 * @param id 觸發器的識別碼。
 * @param factory 建立觸發器的函式。
 * @param onRegistered 觸發器寫入後帶著它呼叫。
 */
fun <T : CriterionTrigger<*>> registerCriterion(registrar: ModRegistrar, id: ResourceLocation, factory: () -> T, onRegistered: (T) -> Unit) {
    registrar.afterRegistration {
        val trigger = factory()
        check(trigger.id == id) { "Criterion trigger ${trigger.id} is registered as $id" }
        onRegistered(CriteriaTriggers.register(trigger))
    }
}
