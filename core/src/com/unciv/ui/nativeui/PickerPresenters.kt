package com.unciv.ui.nativeui

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.Religion
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.pickerscreens.GreatPersonPickerScreen
import com.unciv.ui.screens.pickerscreens.PantheonPickerScreen
import com.unciv.ui.screens.pickerscreens.ReligiousBeliefsPickerScreen

/** Native free great person choice, see [GreatPersonPickerScreen] */
class GreatPersonPresenter(private val screen: GreatPersonPickerScreen) : NativeFormPresenter() {
    private var chosen: BaseUnit? = null

    override fun NativeForm.build() {
        title = "Choose a free great person"
        for (unit in screen.civInfo.greatPeople.getGreatPeople()) {
            val available = screen.isAvailable(unit)
            section(id = "unit.${unit.name}") {
                row("unit.${unit.name}", unit.name,
                    icon = NativeForm.Option(unit.name, unit.name, icon = "UnitIcons/${unit.name}",
                        background = "#2a2f3a", tint = "#ffffff"),
                    tag = if (available) null else "Unavailable",
                    enabled = available, selected = chosen == unit) { chosen = unit }
                if (chosen == unit) text("unit.${unit.name}.description", unit.getShortDescription())
            }
        }
        val unit = chosen
        primary(if (unit == null) "Choose a free great person" else "Get [${unit.name}]",
            enabled = unit != null, symbol = "person.crop.circle.badge.plus") {
            if (unit != null) {
                SoundPlayer.play(UncivSound.Choir)
                screen.choose(unit)
            }
        }
    }
}

private fun beliefIcon(belief: Belief): NativeForm.Option {
    val name = if (ImageGetter.religionIconExists(belief.name)) belief.name else belief.type.name
    return NativeForm.Option(belief.name, belief.name, icon = "ReligionIcons/$name",
        background = belief.type.color.ifEmpty { "#2a2f3a" }, tint = "#ffffff")
}

private fun Belief.effects() = uniqueObjects.filter { !it.isHiddenToUsers() }
    .joinToString("\n") { it.getDisplayText().tr() }

/** Native pantheon choice, see [PantheonPickerScreen] */
class PantheonPresenter(private val screen: PantheonPickerScreen) : NativeFormPresenter() {
    private val civ get() = screen.pickingCiv
    private var selected: Belief? = null

    override fun NativeForm.build() {
        title = "Choose a pantheon"
        closeSymbol = "chevron.left"
        onClose { UncivGame.Current.popScreen() }

        section("Pantheon", id = "pantheons") {
            for (belief in civ.gameInfo.ruleset.beliefs.values) {
                if (belief.type != BeliefType.Pantheon) continue
                val taken = civ.religionManager.getReligionWithBelief(belief) != null
                val available = !taken && screen.beliefIsAllowed(belief, civ)
                row("belief.${belief.name}", belief.name, detail = belief.effects(), icon = beliefIcon(belief),
                    tag = when { taken -> "Taken"; !available -> "Unavailable"; else -> null },
                    tagStyle = "bad", enabled = available, selected = selected == belief) { selected = belief }
            }
        }

        val belief = selected
        primary(if (belief == null) "Choose a pantheon" else "Follow [${belief.name}]",
            enabled = belief != null, symbol = "sparkles") {
            if (belief == null) return@primary
            SoundPlayer.play(UncivSound.Choir)
            civ.religionManager.chooseBeliefs(listOf(belief), useFreeBeliefs = civ.religionManager.usingFreeBeliefs())
            UncivGame.Current.popScreen()
        }
    }
}

/** Native religion founding and enhancing, see [ReligiousBeliefsPickerScreen] */
class ReligionPresenter(private val screen: ReligiousBeliefsPickerScreen) : NativeFormPresenter() {
    private val civ get() = screen.pickingCiv
    private val gameInfo get() = civ.gameInfo
    private val ruleset get() = gameInfo.ruleset
    private val currentReligion = civ.religionManager.religion ?: Religion("None", civ.gameInfo, civ)

    private val slotTypes: List<BeliefType> =
        screen.numberOfBeliefsCanChoose.flatMap { entry -> List(entry.value) { entry.key } }
    private val chosen = arrayOfNulls<Belief>(slotTypes.size)

    private var religionName: String? = if (screen.pickIconAndName) null else currentReligion.name
    private var displayName: String? = if (screen.pickIconAndName) null else currentReligion.getReligionDisplayName()
    private var nameError: String? = null

    override fun NativeForm.build() {
        title = if (screen.pickIconAndName) "Choose a Religion" else "Enhance [${currentReligion.getReligionDisplayName()}]"
        closeSymbol = "chevron.left"
        onClose { UncivGame.Current.popScreen() }

        if (screen.pickIconAndName) nameSection()

        val existing = currentReligion.getAllBeliefsOrdered().toList()
        if (existing.isNotEmpty()) section("Beliefs", id = "existing") {
            for (belief in existing)
                row("existing.${belief.name}", belief.name, detail = belief.effects(), icon = beliefIcon(belief),
                    tag = belief.type.name, tagStyle = "good")
        }

        section("Choose beliefs", id = "slots") {
            for ((index, type) in slotTypes.withIndex()) {
                val options = availableBeliefs(type, index).map { belief ->
                    val icon = beliefIcon(belief)
                    NativeForm.Option(belief.name, belief.name, detail = belief.effects(),
                        icon = icon.icon, background = icon.background, tint = icon.tint)
                }
                choice("slot.$index",
                    if (type == BeliefType.Any) "Choose any belief!" else "Choose a [$type] belief!",
                    chosen[index]?.name ?: "", options, style = "list",
                    detail = chosen[index]?.effects(),
                    icon = chosen[index]?.let { beliefIcon(it).icon }) { picked ->
                    chosen[index] = ruleset.beliefs[picked]
                }
            }
        }

        val ready = religionName != null && displayName != null && chosen.all { it != null }
        val label = when {
            !screen.pickIconAndName -> "Enhance [${currentReligion.getReligionDisplayName()}]"
            displayName != null -> "Found [$displayName]"
            else -> "Choose a Religion"
        }
        primary(label, enabled = ready, symbol = "sparkles") {
            if (!ready) return@primary
            SoundPlayer.play(UncivSound.Choir)
            val manager = civ.religionManager
            if (manager.religionState == ReligionState.FoundingReligion)
                manager.foundReligion(displayName!!, religionName!!)
            manager.chooseBeliefs(chosen.map { it!! }, manager.usingFreeBeliefs())
            UncivGame.Current.popScreen()
        }
    }

    private fun NativeForm.nameSection() = section("Choose an Icon and name for your Religion", id = "name") {
        val taken = gameInfo.religions.keys
        choice("icon", "Icon", religionName ?: "",
            ruleset.religions.filter { it !in taken }.map {
                NativeForm.Option(it, it, icon = "ReligionIcons/$it", background = "#2a2f3a", tint = "#ffffff")
            }, style = "list", icon = religionName?.let { "ReligionIcons/$it" }) { picked ->
            religionName = picked
            displayName = picked
            nameError = null
        }
        if (religionName != null) {
            field("displayName", "Choose a name for your religion", displayName ?: "", detail = nameError) { name ->
                val trimmed = name.trim()
                val valid = trimmed.isNotEmpty() && trimmed != Constants.noReligionName
                    && ruleset.religions.none { it == trimmed } && gameInfo.religions.values.none { it.name == trimmed }
                if (valid || trimmed == religionName) {
                    displayName = trimmed
                    nameError = null
                } else nameError = "Invalid name"
            }
        }
    }

    /** The beliefs a slot may take; mirrors [ReligiousBeliefsPickerScreen]'s rules */
    private fun availableBeliefs(type: BeliefType, slot: Int): List<Belief> {
        val founderManager = currentReligion.foundingCiv.religionManager
        return ruleset.beliefs.values.filter { belief ->
            (belief.type == type || type == BeliefType.Any)
                && chosen.withIndex().none { it.index != slot && it.value == belief }
                && !currentReligion.hasBelief(belief.name)
                && founderManager.getReligionWithBelief(belief).let { it == null || it == currentReligion }
                && belief.getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)
                    .none { !it.conditionalsApply(civ.state) }
                && belief.getMatchingUniques(UniqueType.Unavailable, civ.state).none()
        }
    }
}
