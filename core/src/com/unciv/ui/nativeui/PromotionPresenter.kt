package com.unciv.ui.nativeui

import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.pickerscreens.PromotionTree

/**
 * Native promotions: the unit's experience, its statuses, and its promotions in tree order (each
 * after its prerequisite). Selecting one shows what it does and the path to it under it; the primary
 * button adopts the path, exactly as [PromotionPickerScreen] does.
 */
class PromotionPresenter(private val screen: PromotionPickerScreen) : NativeFormPresenter() {
    private val unit get() = screen.unit
    private var selected: PromotionTree.PromotionNode? = null

    override fun NativeForm.build() {
        title = unit.displayName()
        val promotions = unit.promotions
        subtitle = "[${promotions.XP}]/[${promotions.xpForNextPromotion()}] XP"
        closeSymbol = "chevron.left"
        onClose { screen.closeNatively() }

        if (screen.canChangeState) section(id = "unit") {
            field("name", "Choose name for [${unit.baseUnit.name}]", unit.instanceName ?: "", placeholder = unit.baseUnit.name) { name ->
                // An empty name restores the unit type's name
                if (name != unit.name) unit.instanceName = name.trim().ifEmpty { null }
            }
            if (screen.canPromoteNow && screen.canSaveUnitPromotion())
                toggle("saveDefault", "Default promotions for [${unit.baseUnit.name}]", screen.saveUnitPromotion) {
                    screen.saveUnitPromotion = it
                }
        }

        if (unit.statusMap.isNotEmpty()) section("Status", id = "statuses") {
            for (status in unit.statusMap.values.sortedBy { it.turnsLeft }) {
                row("status.${status.name}", status.name,
                    detail = unit.civ.gameInfo.ruleset.unitPromotions[status.name]?.getDescription(emptySet()),
                    icon = icon(status.name, adopted = true), value = "[${status.turnsLeft}] turns".tr())
            }
        }

        section("Promotions", id = "promotions") {
            for ((node, depth) in ordered()) {
                val pickable = screen.isPickable(node)
                row("promotion.${node.promotion.name}", node.promotion.name,
                    detail = when {
                        depth > 0 && !node.isAdopted && !pickable ->
                            "{Requires} " + node.parents.joinToString(" / ") { "{${it.promotion.name}}" }
                        else -> null
                    },
                    icon = icon(node.promotion.name, node.isAdopted, pickable),
                    tag = when {
                        node.isAdopted -> "Adopted"
                        pickable -> "Available"
                        else -> null
                    },
                    tagStyle = if (node.isAdopted) "good" else "accent",
                    selected = selected == node) { selected = node }
                // Right under the row: a section at the top would be scrolled out of sight
                if (selected == node) selectedDetails(node)
            }
        }

        val node = selected
        if (screen.canPromoteNow && node != null && !node.isAdopted)
            primary("Pick promotion", enabled = screen.isPickable(node), symbol = "star.fill") {
                screen.acceptNatively(node)
            }
    }

    private fun NativeForm.Section.selectedDetails(node: PromotionTree.PromotionNode) {
        val id = "promotion.${node.promotion.name}"
        val path = screen.tree.getPathTo(node.promotion)
        val ambiguous = node.pathIsAmbiguous && node.distanceToAdopted > 1 && screen.tree.canBuyUpTo(node.promotion)
        when {
            node.isAdopted -> {}
            ambiguous -> text("$id.path", "Path to [${node.promotion.name}] is ambiguous")
            path.size > 1 && screen.isPickable(node) ->
                text("$id.path", path.joinToString(" → ") { it.name.tr() })
        }
        text("$id.description", node.promotion.getDescription(screen.tree.possiblePromotions))
    }

    /** Promotions with their depth, each after its preferred parent, roots first */
    private fun ordered(): List<Pair<PromotionTree.PromotionNode, Int>> {
        val result = ArrayList<Pair<PromotionTree.PromotionNode, Int>>()
        val placed = HashSet<PromotionTree.PromotionNode>()
        fun place(node: PromotionTree.PromotionNode, depth: Int) {
            if (!placed.add(node)) return
            result += node to depth
            for (child in node.children)
                if (child.preferredParent == null || child.preferredParent == node) place(child, depth + 1)
        }
        for (root in screen.tree.allRoots()) place(root, 0)
        for (node in screen.tree.allNodes()) place(node, 1)
        return result
    }

    private fun icon(name: String, adopted: Boolean, pickable: Boolean = false): NativeForm.Option {
        val (withoutBrackets, _, base) = Promotion.getBaseNameAndLevel(name)
        val path = listOf(
            "UnitPromotionIcons/$withoutBrackets",
            "UnitPromotionIcons/$base",
            "UnitIcons/${base.removeSuffix(" ability")}",
        ).firstOrNull { ImageGetter.imageExists(it) } ?: "UnitPromotionIcons/$base"
        return NativeForm.Option(name, name, icon = path,
            background = when { adopted -> "#3a2f0c"; pickable -> "#1d3a52"; else -> "#1a1d24" },
            tint = when { adopted -> "#f5c542"; pickable -> "#ffffff"; else -> "#6b7280" })
    }
}
