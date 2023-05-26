package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.id
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent

object SoundRegistry {

    //TODO 缺少了一些音效，待日後補上
    val chii = SoundEvent.of(id("chii"))
    val pon = SoundEvent.of(id("pon"))
    val kan = SoundEvent.of(id("kan"))
    val riichi = SoundEvent.of(id("riichi"))
    val ron = SoundEvent.of(id("ron"))
    val tsumo = SoundEvent.of(id("tsumo"))


    fun register() {
        Registry.register(Registries.SOUND_EVENT, id("chii"), chii)
        Registry.register(Registries.SOUND_EVENT, id("pon"), pon)
        Registry.register(Registries.SOUND_EVENT, id("kan"), kan)
        Registry.register(Registries.SOUND_EVENT, id("riichi"), riichi)
        Registry.register(Registries.SOUND_EVENT, id("ron"), ron)
        Registry.register(Registries.SOUND_EVENT, id("tsumo"), tsumo)
    }

}