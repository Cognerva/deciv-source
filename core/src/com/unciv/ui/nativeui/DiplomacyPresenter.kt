package com.unciv.ui.nativeui

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.ui.components.input.activate
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.diplomacyscreen.TradeTable
import com.unciv.Constants
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.logic.trade.TradeOffersList
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.translations.tr
import com.unciv.view.ForeignCivView

/**
 * Native diplomacy: the civilizations we know, and for the selected one what the Scene2D panel
 * says and offers. The panel is still built by [DiplomacyScreen] (it holds all the rules for
 * what is possible); this reads its text and buttons and presses them on tap. A trade is drawn
 * natively from the Scene2D [TradeTable]'s staged offers, see [trade].
 */
class DiplomacyPresenter(private val screen: DiplomacyScreen) : NativeFormPresenter() {
    private val viewingCiv get() = screen.viewingCiv
    var selected: Civilization? = null

    private fun badge(civ: Civilization) = NativeForm.Option(
        civ.civName, civ.civName, icon = if (civ.isCityState) "CityStateIcons/${civ.cityStateType.name}" else "NationIcons/${civ.nation.name}",
        background = "#" + civ.nation.getOuterColor().toString().take(6),
        tint = "#" + civ.nation.getInnerColor().toString().take(6),
    )

    override fun NativeForm.build() {
        val tradeTable = screen.rightSideTable.children.firstOrNull { it is TradeTable } as? TradeTable
        if (tradeTable != null) return trade(tradeTable)

        title = "Diplomacy"
        closeSymbol = "chevron.left"
        onClose { UncivGame.Current.popScreen() }

        val known = viewingCiv.diplomacyFunctions.getKnownCivsSorted().toList()
        val current = selected?.takeIf { it in known }
        if (current != null) {
            val entries = NativePopupReader.readEntries(screen.rightSideTable)
            section(current.civName, id = "selected", icon = "NationIcons/${current.nation.name}") {
                var textIndex = 0
                for ((index, entry) in entries.withIndex()) when (entry) {
                    is NativePopupReader.Entry.Text -> {
                        // The panel's first line repeats the civ's name
                        if (textIndex++ == 0 && entry.text.contains(current.civName)) continue
                        row("panel.$index", entry.text, translate = false)
                    }
                    is NativePopupReader.Entry.Action ->
                        button("panel.$index", entry.button.text.toString(),
                            role = if (entry.destructive) "destructive" else "default",
                            enabled = !entry.button.isDisabled) { entry.button.activate() }
                    else -> {}
                }
            }
        }

        section("Civilizations", id = "civs") {
            for (civ in known) {
                val diplomacy = civ.getDiplomacyManager(viewingCiv) ?: continue
                val level = diplomacy.relationshipLevel()
                val atWar = civ.isAtWarWith(viewingCiv)
                row("civ.${civ.civName}", civ.civName, icon = badge(civ),
                    detail = when {
                        civ.isDefeated() -> "Defeated"
                        civ.isCityState -> "${civ.cityStateType.name} · ${level.name}"
                        else -> level.name
                    },
                    tag = when {
                        atWar -> "War"
                        diplomacy.diplomaticStatus == DiplomaticStatus.DefensivePact -> "Defensive Pact"
                        civ.isCityState && level == RelationshipLevel.Ally -> "Ally"
                        civ.isCityState && civ.questManager.haveQuestsFor(viewingCiv) -> "Quest"
                        else -> null
                    },
                    tagStyle = when {
                        atWar -> "bad"
                        level >= RelationshipLevel.Friend -> "good"
                        else -> "accent"
                    },
                    selected = civ == current) {
                    selected = civ
                    screen.updateRightSide(civ)
                }
            }
        }
    }

    // region Trade

    /**
     * Both sides' staged offers, then what each side could add. Tapping moves an offer like the
     * Scene2D columns do; gold amounts are set with a slider instead of a number popup.
     */
    private fun NativeForm.trade(table: TradeTable) {
        val tradeView = table.tradeView
        val columns = table.offerColumnsTable
        val ourCiv = columns.ourCiv
        val theirCiv = columns.theirCiv
        val them = viewingCiv.gameInfo.getCivilization(theirCiv.civName)

        title = "Trade"
        subtitle = theirCiv.civName
        closeSymbol = "chevron.left"
        onClose { screen.updateRightSide(them) }

        val ourUntradable = ourCiv.getPerTurnResourcesWithOriginsForTrade().removeAll(Constants.tradable)
        val theirUntradable = theirCiv.getPerTurnResourcesWithOriginsForTrade().removeAll(Constants.tradable)

        stagedSection("Our trade offer", "ours", tradeView.ourStagedOffers(), tradeView.theirStagedOffers(), ourCiv, table)
        stagedSection("[${theirCiv.civName}]'s trade offer", "theirs", tradeView.theirStagedOffers(), tradeView.ourStagedOffers(), theirCiv, table)
        availableSections("Our items", "ourItems",
            tradeView.ourAvailableOffers().without(tradeView.ourStagedOffers()), tradeView.theirAvailableOffers(),
            ourUntradable, tradeView.ourStagedOffers(), tradeView.theirStagedOffers(), ourCiv, theirCiv, table)
        availableSections("[${theirCiv.civName}]'s items", "theirItems",
            tradeView.theirAvailableOffers().without(tradeView.theirStagedOffers()), tradeView.ourAvailableOffers(),
            theirUntradable, tradeView.theirStagedOffers(), tradeView.ourStagedOffers(), ourCiv, theirCiv, table)

        val button = table.offerButton
        primary(button.text.toString().replace("\n", " "), enabled = !button.isDisabled,
            symbol = if (tradeView.hasPendingOfferFromUs()) "arrow.uturn.backward" else "arrow.left.arrow.right") {
            button.activate()
        }
    }

    private fun NativeForm.stagedSection(
        title: String, id: String, offers: TradeOffersList, counterOffers: TradeOffersList,
        owner: ForeignCivView, table: TradeTable
    ) = section(title, id = id) {
        if (offers.isEmpty()) text("$id.empty", "Nothing offered yet")
        for ((index, offer) in offers.withIndex()) {
            if (offer.type == TradeOfferType.Gold || offer.type == TradeOfferType.Gold_Per_Turn) {
                val max = if (offer.type == TradeOfferType.Gold) owner.gold else owner.getGoldPerTurn()
                slider("$id.$index", offerLabel(offer), offer.amount.toFloat(), 0f, max.coerceAtLeast(offer.amount).toFloat(),
                    if (offer.type == TradeOfferType.Gold) 10f else 1f) { amount ->
                    offer.amount = amount.toInt()
                    if (offer.amount <= 0) offers.remove(offer)
                    table.changedNatively()
                }
                continue
            }
            val removable = offer.name != Constants.peaceTreaty
            row("$id.$index", offerLabel(offer), icon = offerIcon(offer), enabled = removable,
                tag = if (removable) "Remove" else null) {
                val transferred = minOf(amountPerClick(offer), offer.amount)
                columns(table).addOffer(offer.copy(amount = -transferred), offers, counterOffers)
            }
        }
    }

    private fun NativeForm.availableSections(
        title: String, id: String, offers: TradeOffersList, otherSideOffers: TradeOffersList,
        untradable: ResourceSupplyList, staged: TradeOffersList, counterStaged: TradeOffersList,
        ourCiv: ForeignCivView, theirCiv: ForeignCivView, table: TradeTable
    ) {
        val groups = offers.groupBy { groupName(it.type) }
        if (groups.isEmpty()) return section(title, id = id) { text("$id.empty", "Nothing to trade") }
        for ((group, groupOffers) in groups) {
            // Long lists, e.g. technologies late in the game, start folded
            val heading = title.tr(hideIcons = true, hideStats = true) + " · " + group.tr(hideIcons = true, hideStats = true)
            section(heading, id = "$id.$group", expanded = groupOffers.size <= 6) {
                val sorted = groupOffers.sortedWith(compareBy(
                    { if (UncivGame.Current.settings.orderTradeOffersByAmount) -it.amount else 0 },
                    { if (it.type == TradeOfferType.City) it.getOfferText() else it.name.tr() }))
                for ((index, offer) in sorted.withIndex()) {
                    val tradable = offer.isTradable() && offer.name != Constants.peaceTreaty &&
                        (offer.name != Constants.researchAgreement ||
                            ourCiv.gold + theirCiv.gold > ourCiv.getResearchAgreementCost(theirCiv) * 2)
                    // The other side has none of this resource: a unique suggestion
                    val unique = offer.type in listOf(TradeOfferType.Luxury_Resource, TradeOfferType.Strategic_Resource) &&
                        otherSideOffers.all { it.type != offer.type || it.name != offer.name || it.amount < 0 }
                    row("$id.$group.$index", offerLabel(offer, untradable.sumBy(offer.name)), icon = offerIcon(offer),
                        tag = if (tradable && unique) "Unique" else null, tagStyle = "good", enabled = tradable) {
                        when (offer.type) {
                            TradeOfferType.Gold, TradeOfferType.Gold_Per_Turn -> {
                                // Start with a modest amount; the slider in the offer sets the rest
                                if (staged.none { it.type == offer.type })
                                    staged.add(offer.copy(amount = minOf(if (offer.type == TradeOfferType.Gold) 50 else 5, offer.amount)))
                                table.changedNatively()
                            }
                            else -> columns(table).addOffer(offer.copy(amount = minOf(amountPerClick(offer), offer.amount)), staged, counterStaged)
                        }
                    }
                }
            }
        }
    }

    /** The Scene2D offer text, without the stat glyph and hourglass the row's icon makes redundant */
    private fun offerLabel(offer: TradeOffer, untradable: Int = 0): String {
        if (offer.type.numberType != TradeOfferType.TradeTypeNumberType.Gold) return offer.getOfferText(untradable)
        val name = offer.name.tr(hideIcons = true, hideStats = true) + " (${offer.amount})"
        return if (offer.duration > 0) name + "\n" + "[${offer.duration}] turns".tr() else name
    }

    private fun columns(table: TradeTable) = table.offerColumnsTable

    private fun amountPerClick(offer: TradeOffer) = when (offer.type) {
        TradeOfferType.Gold -> 50
        TradeOfferType.Treaty -> Int.MAX_VALUE
        else -> 1
    }

    private fun groupName(type: TradeOfferType) = when (type) {
        TradeOfferType.Embassy, TradeOfferType.Treaty, TradeOfferType.Agreement, TradeOfferType.Introduction -> "Agreements"
        TradeOfferType.Gold, TradeOfferType.Gold_Per_Turn -> "Gold"
        TradeOfferType.Luxury_Resource -> "Luxury resources"
        TradeOfferType.Strategic_Resource -> "Strategic resources"
        TradeOfferType.Stockpiled_Resource -> "Stockpiled resources"
        TradeOfferType.Technology -> "Technologies"
        TradeOfferType.WarDeclaration -> "Declarations of war"
        TradeOfferType.PeaceProposal -> "Peace Proposals"
        TradeOfferType.City -> "Cities"
    }

    private fun offerIcon(offer: TradeOffer): NativeForm.Option {
        fun nation(civName: String): NativeForm.Option {
            val civ = viewingCiv.gameInfo.getCivilizationOrNull(civName) ?: return NativeForm.Option("", "", symbol = "flag.fill")
            return badge(civ)
        }
        return when (offer.type) {
            TradeOfferType.Luxury_Resource, TradeOfferType.Strategic_Resource, TradeOfferType.Stockpiled_Resource ->
                NativeForm.Option("", "", icon = "ResourceIcons/${offer.name}")
            TradeOfferType.Technology -> NativeForm.Option("", "", icon = "TechIcons/${offer.name}", background = "#1d3a52", tint = "#ffffff")
            TradeOfferType.Gold, TradeOfferType.Gold_Per_Turn -> NativeForm.Option("", "", icon = "StatIcons/Gold")
            TradeOfferType.WarDeclaration, TradeOfferType.PeaceProposal, TradeOfferType.Introduction -> nation(offer.name)
            TradeOfferType.City -> NativeForm.Option("", "", symbol = "building.2.fill", tint = "#e8a84d")
            TradeOfferType.Embassy -> NativeForm.Option("", "", symbol = "building.columns.fill", tint = "#e8a84d")
            TradeOfferType.Treaty, TradeOfferType.Agreement -> NativeForm.Option("", "", symbol = "doc.text.fill", tint = "#e8a84d")
        }
    }

    // endregion
}
