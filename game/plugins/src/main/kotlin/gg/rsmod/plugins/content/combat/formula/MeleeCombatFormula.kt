@file:Suppress("UNUSED_PARAMETER")

package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import kotlin.math.floor

/**
 * @author Tom <rspsmods@gmail.com>
 */
object MeleeCombatFormula : CombatFormula {

    private val BLACK_MASKS = intArrayOf(Items.BLACK_MASK,
            Items.BLACK_MASK_1, Items.BLACK_MASK_2, Items.BLACK_MASK_3, Items.BLACK_MASK_4,
            Items.BLACK_MASK_5, Items.BLACK_MASK_6, Items.BLACK_MASK_7, Items.BLACK_MASK_8,
            Items.BLACK_MASK_9, Items.BLACK_MASK_10)


    private val MELEE_VOID = intArrayOf(Items.VOID_MELEE_HELM, Items.VOID_KNIGHT_TOP, Items.VOID_KNIGHT_ROBE, Items.VOID_KNIGHT_GLOVES)


    override fun getAccuracy(pawn: Pawn, target: Pawn, specialAttackMultiplier: Double): Double {
        val attack = getAttackRoll(pawn, target, specialAttackMultiplier)
        val defence = when {
            (pawn is Npc && target is Player) && pawn.combatDef.attackStyleType == StyleType.MAGIC_MELEE -> MagicCombatFormula.getDefenceRoll(target)
            else -> getDefenceRoll(pawn, target)
        }

        val accuracy: Double = if (attack > defence) {
            1.0 - (defence + 2.0) / (2.0 * (attack + 1.0))
        } else {
            attack / (2.0 * (defence + 1))
        }
        return accuracy
    }

    override fun getMaxHit(pawn: Pawn, target: Pawn, specialAttackMultiplier: Double, specialPassiveMultiplier: Double): Double {
        val a = if (pawn is Player) getEffectiveStrengthLevel(pawn) else if (pawn is Npc) getEffectiveStrengthLevel(pawn) else 0.0
        val b = getEquipmentStrengthBonus(pawn)

        var base = 0.5 + a * (b + 64.0) / 640.0
        if (pawn is Player) {
            base = applyStrengthSpecials(pawn, target, base, specialAttackMultiplier, specialPassiveMultiplier)
        }
        return base
    }

    private fun getAttackRoll(pawn: Pawn, target: Pawn, specialAttackMultiplier: Double): Int {
        val a = if (pawn is Player) getEffectiveAttackLevel(pawn) else if (pawn is Npc) getEffectiveAttackLevel(pawn) else 0.0
        val b = getEquipmentAttackBonus(pawn)

        var maxRoll = a * (b + 64.0)
        if (pawn is Player) {
            maxRoll = applyAttackSpecials(pawn, target, maxRoll, specialAttackMultiplier)
        }
        return maxRoll.toInt()
    }

    private fun getDefenceRoll(pawn: Pawn, target: Pawn): Int {
        val a = if (target is Player) getEffectiveDefenceLevel(target) else if (target is Npc) getEffectiveDefenceLevel(target) else 0.0
        val b = getEquipmentDefenceBonus(pawn, target)

        var maxRoll = a * (b + 64.0)
        maxRoll = applyDefenceSpecials(target, maxRoll)
        return maxRoll.toInt()
    }

    private fun applyStrengthSpecials(player: Player, target: Pawn, base: Double, specialAttackMultiplier: Double, specialPassiveMultiplier: Double): Double {
        var hit = base

        hit *= getEquipmentMultiplier(player)

        hit *= specialAttackMultiplier

        if (target.hasPrayerIcon(PrayerIcon.PROTECT_FROM_MELEE)) {
            hit *= 0.6
        }

        if (specialPassiveMultiplier == 1.0) {
            hit = applyPassiveMultiplier(player, target, hit)
        } else {
            hit *= specialPassiveMultiplier
        }

        hit *= getDamageDealMultiplier(player)
        hit *= getDamageTakeMultiplier(target)
        return hit
    }

    private fun applyAttackSpecials(player: Player, target: Pawn, base: Double, specialAttackMultiplier: Double): Double {
        var hit = base
        hit *= getEquipmentMultiplier(player)
        hit *= specialAttackMultiplier
        return hit
    }

    private fun applyDefenceSpecials(target: Pawn, base: Double): Double {
        var hit = base

        // TODO: find if there's any defence specials for 667
        /**if (target is Player && isWearingTorag(target) && target.hasEquipped(EquipmentType.AMULET, Items.AMULET_OF_THE_DAMNED_FULL)) {
            val lost = (target.getMaxHp() - target.getCurrentHp()) / 100.0
            val max = target.getMaxHp() / 100.0
            hit *= (1.0 + (lost * max))
            hit = Math.floor(hit)
        }**/

        return hit
    }

    private fun getEquipmentStrengthBonus(pawn: Pawn): Double = when (pawn) {
        is Player -> pawn.getStrengthBonus().toDouble()
        is Npc -> pawn.getStrengthBonus().toDouble()
        else -> throw IllegalArgumentException("Invalid pawn type. $pawn")
    }

    private fun getEquipmentAttackBonus(pawn: Pawn): Double {
        val bonus = when (val combatStyle = CombatConfigs.getCombatStyle(pawn)) {
            StyleType.STAB -> BonusSlot.ATTACK_STAB
            StyleType.SLASH -> BonusSlot.ATTACK_SLASH
            StyleType.CRUSH -> BonusSlot.ATTACK_CRUSH
            StyleType.MAGIC_MELEE -> BonusSlot.ATTACK_STAB
            else -> throw IllegalStateException("Invalid combat style. $combatStyle")
        }
        return pawn.getBonus(bonus).toDouble()
    }

    private fun getEquipmentDefenceBonus(pawn: Pawn, target: Pawn): Double {
        val bonus = when (val combatStyle = CombatConfigs.getCombatStyle(pawn)) {
            StyleType.STAB -> BonusSlot.DEFENCE_STAB
            StyleType.SLASH -> BonusSlot.DEFENCE_SLASH
            StyleType.CRUSH -> BonusSlot.DEFENCE_CRUSH
            else -> throw IllegalStateException("Invalid combat style. $combatStyle")
        }
        return target.getBonus(bonus).toDouble()
    }

    private fun getEffectiveStrengthLevel(player: Player): Double {
        var effectiveLevel = floor(player.getSkills().getCurrentLevel(Skills.STRENGTH) * getPrayerStrengthMultiplier(player))

        effectiveLevel += when (CombatConfigs.getAttackStyle(player)){
            WeaponStyle.AGGRESSIVE -> 3.0
            WeaponStyle.CONTROLLED -> 1.0
            else -> 1.0
        }

        effectiveLevel += 8.0

        if (player.hasEquipped(MELEE_VOID)) {
            effectiveLevel *= 1.10
        }

        return effectiveLevel
    }

    private fun getEffectiveAttackLevel(player: Player): Double {
        var effectiveLevel = floor(player.getSkills().getCurrentLevel(Skills.ATTACK) * getPrayerAttackMultiplier(player))

        effectiveLevel += when (CombatConfigs.getAttackStyle(player)){
            WeaponStyle.ACCURATE -> 3.0
            WeaponStyle.CONTROLLED -> 1.0
            else -> 0.0
        }

        effectiveLevel += 8.0

        if (player.hasEquipped(MELEE_VOID)) {
            effectiveLevel *= 1.10
            effectiveLevel = floor(effectiveLevel)
        }

        return effectiveLevel
    }

    private fun getEffectiveDefenceLevel(player: Player): Double {
        var effectiveLevel = floor(player.getSkills().getCurrentLevel(Skills.DEFENCE) * getPrayerDefenceMultiplier(player))

        effectiveLevel += when (CombatConfigs.getAttackStyle(player)){
            WeaponStyle.DEFENSIVE -> 3.0
            WeaponStyle.CONTROLLED -> 1.0
            WeaponStyle.LONG_RANGE -> 3.0
            else -> 0.0
        }

        effectiveLevel += 8.0

        return effectiveLevel
    }

    private fun getEffectiveStrengthLevel(npc: Npc): Double {
        var effectiveLevel = npc.stats.getCurrentLevel(NpcSkills.STRENGTH).toDouble()
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getEffectiveAttackLevel(npc: Npc): Double {
        var effectiveLevel = npc.stats.getCurrentLevel(NpcSkills.ATTACK).toDouble()
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getEffectiveDefenceLevel(npc: Npc): Double {
        var effectiveLevel = npc.stats.getCurrentLevel(NpcSkills.DEFENCE).toDouble()
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getPrayerStrengthMultiplier(player: Player): Double = when {
        else -> 1.0
    }

    private fun getPrayerAttackMultiplier(player: Player): Double = when {
        else -> 1.0
    }

    private fun getPrayerDefenceMultiplier(player: Player): Double = when {
        else -> 1.0
    }

    private fun getEquipmentMultiplier(player: Player): Double = when {
        player.hasEquipped(EquipmentType.AMULET, Items.SALVE_AMULET) -> 7.0 / 6.0
        player.hasEquipped(EquipmentType.AMULET, Items.SALVE_AMULET_E) -> 1.2
        // TODO: this should only apply when target is slayer task?
        player.hasEquipped(EquipmentType.HEAD, *BLACK_MASKS) -> 7.0 / 6.0
        else -> 1.0
    }

    private fun applyPassiveMultiplier(pawn: Pawn, target: Pawn, base: Double): Double {
        if (pawn is Player) {
            val world = pawn.world
            val multiplier = when {
                pawn.hasEquipped(EquipmentType.AMULET, Items.BERSERKER_NECKLACE) -> 1.2
                isWearingDharok(pawn) -> {
                    val lost = (pawn.getMaxHp() - pawn.getCurrentHp()) / 100.0
                    val max = pawn.getMaxHp() / 100.0
                    1.0 + (lost * max)
                }
                pawn.hasEquipped(EquipmentType.WEAPON, Items.GADDERHAMMER) && isShade(target) -> if (world.chance(1, 20)) 2.0 else 1.25
                pawn.hasEquipped(EquipmentType.WEAPON, Items.KERIS, Items.KERIS_P) && (isKalphite(target) || isScarab(target)) -> if (world.chance(1, 51)) 3.0 else (4.0 / 3.0)
                else -> 1.0
            }
            if (multiplier == 1.0 && isWearingVerac(pawn)) {
                return base + 1.0
            }
            return base * multiplier
        }
        return base
    }

    private fun getDamageDealMultiplier(pawn: Pawn): Double = pawn.attr[Combat.DAMAGE_DEAL_MULTIPLIER] ?: 1.0

    private fun getDamageTakeMultiplier(pawn: Pawn): Double = pawn.attr[Combat.DAMAGE_TAKE_MULTIPLIER] ?: 1.0

    private fun isDemon(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.DEMON)
        }
        return false
    }

    private fun isShade(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.SHADE)
        }
        return false
    }

    private fun isKalphite(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.KALPHITE)
        }
        return false
    }

    private fun isScarab(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.SCARAB)
        }
        return false
    }

    private fun isWearingDharok(pawn: Pawn): Boolean {
        if (pawn.entityType.isPlayer) {
            val player = pawn as Player
            return player.hasEquipped(EquipmentType.HEAD, Items.DHAROKS_HELM, Items.DHAROKS_HELM_25, Items.DHAROKS_HELM_50, Items.DHAROKS_HELM_75, Items.DHAROKS_HELM_100)
                    && player.hasEquipped(EquipmentType.WEAPON, Items.DHAROKS_GREATAXE, Items.DHAROKS_GREATAXE_25, Items.DHAROKS_GREATAXE_50, Items.DHAROKS_GREATAXE_75, Items.DHAROKS_GREATAXE_100)
                    && player.hasEquipped(EquipmentType.CHEST, Items.DHAROKS_PLATEBODY, Items.DHAROKS_PLATEBODY_25, Items.DHAROKS_PLATEBODY_50, Items.DHAROKS_PLATEBODY_75, Items.DHAROKS_PLATEBODY_100)
                    && player.hasEquipped(EquipmentType.LEGS, Items.DHAROKS_PLATELEGS, Items.DHAROKS_PLATELEGS_25, Items.DHAROKS_PLATELEGS_50, Items.DHAROKS_PLATELEGS_75, Items.DHAROKS_PLATELEGS_100)
        }
        return false
    }

    private fun isWearingVerac(pawn: Pawn): Boolean {
        if (pawn.entityType.isPlayer) {
            val player = pawn as Player
            return player.hasEquipped(EquipmentType.HEAD, Items.VERACS_HELM, Items.VERACS_HELM_25, Items.VERACS_HELM_50, Items.VERACS_HELM_75, Items.VERACS_HELM_100)
                    && player.hasEquipped(EquipmentType.WEAPON, Items.VERACS_FLAIL, Items.VERACS_FLAIL_25, Items.VERACS_FLAIL_50, Items.VERACS_FLAIL_75, Items.VERACS_FLAIL_100)
                    && player.hasEquipped(EquipmentType.CHEST, Items.VERACS_BRASSARD, Items.VERACS_BRASSARD_25, Items.VERACS_BRASSARD_50, Items.VERACS_BRASSARD_75, Items.VERACS_BRASSARD_100)
                    && player.hasEquipped(EquipmentType.LEGS, Items.VERACS_PLATESKIRT, Items.VERACS_PLATESKIRT_25, Items.VERACS_PLATESKIRT_50, Items.VERACS_PLATESKIRT_75, Items.VERACS_PLATESKIRT_100)
        }
        return false
    }

    private fun isWearingTorag(player: Player): Boolean {
        return player.hasEquipped(EquipmentType.HEAD, Items.TORAGS_HELM, Items.TORAGS_HELM_25, Items.TORAGS_HELM_50, Items.TORAGS_HELM_75, Items.TORAGS_HELM_100)
                && player.hasEquipped(EquipmentType.WEAPON, Items.TORAGS_HAMMERS, Items.TORAGS_HAMMERS_25, Items.TORAGS_HAMMERS_50, Items.TORAGS_HAMMERS_75, Items.TORAGS_HAMMERS_100)
                && player.hasEquipped(EquipmentType.CHEST, Items.TORAGS_PLATEBODY, Items.TORAGS_PLATEBODY_25, Items.TORAGS_PLATEBODY_50, Items.TORAGS_PLATEBODY_75, Items.TORAGS_PLATEBODY_100)
                && player.hasEquipped(EquipmentType.LEGS, Items.TORAGS_PLATELEGS, Items.TORAGS_PLATELEGS_25, Items.TORAGS_PLATELEGS_50, Items.TORAGS_PLATELEGS_75, Items.TORAGS_PLATELEGS_100)
    }
}