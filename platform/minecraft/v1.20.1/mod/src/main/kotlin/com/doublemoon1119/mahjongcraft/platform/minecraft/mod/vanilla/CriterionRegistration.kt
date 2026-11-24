package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.advancements.CriteriaTriggers
import net.minecraft.advancements.CriterionTrigger
import net.minecraft.resources.ResourceLocation

/**
 * 登記一個進度觸發器，讓進度檔可以用 [id] 引用它。
 *
 * @param T 觸發器型別。
 * @param id 觸發器的識別碼。
 * @param trigger 觸發器。
 * @return 登記後的觸發器。
 */
fun <T : CriterionTrigger<*>> registerCriterion(id: ResourceLocation, trigger: T): T {
    check(trigger.id == id) { "Criterion trigger ${trigger.id} is registered as $id" }
    return CriteriaTriggers.register(trigger)
}
