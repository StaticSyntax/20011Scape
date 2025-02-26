package gg.rsmod.plugins.content.skills.fishing

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.messageBox
import gg.rsmod.plugins.api.ext.player

object Fishing {

    private const val waitTime = 2

    suspend fun fish(task: QueueTask, fishingSpot: Npc, tool: FishingTool) {
        val player = task.player

        if (!canFish(player, tool, fishingSpot)) {
            return
        }

        player.message(introMessage(tool))

        while (canFish(player, tool, fishingSpot)) {
            player.animate(tool.animation)
            task.wait(waitTime)
            val level = player.getSkills().getCurrentLevel(Skills.FISHING)
            for (fish in tool.relevantFish(level)) {
                if (fish.roll(level)) {
                    handleFishCaught(player, tool, fish)
                    break
                }
            }
            task.wait(waitTime)
        }
        player.animate(-1)
    }

    private fun canFish(player: Player, tool: FishingTool, fishingSpot: Npc): Boolean {
        // TODO: are these the correct messages?

        if (!fishingSpot.tile.isWithinRadius(player.tile, 1) && fishingSpot.id != Npcs.ROCKTAIL_SHOAL) {
            return false
        }

        if (!fishingSpot.tile.isWithinRadius(player.tile, 2) && fishingSpot.id == Npcs.ROCKTAIL_SHOAL) {
            return false
        }

        if (!hasItem(player, tool.id)) {
            player.queue {
                messageBox("You need a ${tool.identifier.lowercase()} to catch these fish.")
            }
            return false
        }

        if (!hasItem(player, tool.baitId)) {
            player.message("You don't have bait to fish here.")
            return false
        }

        if (player.getSkills().getCurrentLevel(Skills.FISHING) < tool.level) {
            player.message("You need a fishing level of ${tool.level} to fish here.")
            return false
        }

        if (player.inventory.isFull && (tool.baitId == null || player.inventory.getItemCount(tool.baitId) > 1)) {
            player.message("You don't have enough space in your inventory.")
            return false
        }

        return true
    }

    private fun hasItem(player: Player, itemId: Int?) = itemId == null || player.inventory.contains(itemId)

    private fun handleFishCaught(player: Player, tool: FishingTool, fish: Fish) {
        player.filterableMessage(caughtMessage(fish))
        // player.playSound() TODO: figure out the correct sound for caught fish

        tool.baitId?.let { player.inventory.remove(it) }
        player.inventory.add(fish.id)

        player.addXp(Skills.FISHING, fish.xp)
    }

    private fun introMessage(tool: FishingTool) =
        if (tool == FishingTool.SMALL_FISHING_NET) {
            "You cast out your net..."
        } else {
            "You attempt to catch a fish."
        }

    private fun caughtMessage(fish: Fish) =
        if (fish == Fish.SHRIMP || fish == Fish.ANCHOVIES) {
            "You catch some ${fish.name.lowercase()}."
        } else {
            "You catch a ${fish.name.lowercase()}."
        }
}
