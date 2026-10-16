package com.unciv.ui.nativeui

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.models.UncivSound
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import kotlin.math.ceil

/**
 * Native social policies: culture progress, then a section per branch with its adoption state and
 * its policies. Selecting a policy shows what it does under it; the primary button adopts it, exactly as
 * [PolicyPickerScreen] would.
 */
class PolicyPresenter(private val screen: PolicyPickerScreen) : NativeFormPresenter() {
    private val civ: Civilization get() = screen.viewingCiv
    private val manager get() = civ.policies
    private var selected: Policy? = null

    init {
        selected = screen.initialSelection?.let { civ.gameInfo.ruleset.policies[it] }
    }

    private fun Policy.isPickable() =
        civ.isCurrentPlayer() && screen.canChangeState && !civ.isDefeated()
            && !manager.isAdopted(name) && policyBranchType != PolicyBranchType.BranchComplete
            && manager.isAdoptable(this) && manager.canAdoptPolicy()

    private fun PolicyBranch.isPickable() =
        civ.isCurrentPlayer() && screen.canChangeState && !civ.isDefeated()
            && !manager.isAdopted(name) && manager.isAdoptable(this) && manager.canAdoptPolicy()

    private fun icon(name: String, adopted: Boolean, pickable: Boolean) = NativeForm.Option(
        name, name, icon = "PolicyIcons/$name",
        background = when { adopted -> "#3a2f0c"; pickable -> "#1d3a52"; else -> "#1a1d24" },
        tint = when { adopted -> "#f5c542"; pickable -> "#ffffff"; else -> "#6b7280" },
    )

    override fun NativeForm.build() {
        title = "Social Policies"
        closeSymbol = "chevron.left"
        onClose { UncivGame.Current.popScreen() }

        val culture = civ.stats.statsForNextTurn.culture
        val needed = manager.getCultureNeededForNextPolicy()
        subtitle = when {
            manager.allPoliciesAdopted(checkEra = false) -> "All policies adopted"
            manager.freePolicies > 0 -> "[${manager.freePolicies}] free policies".tr()
            culture > 0 -> "[${manager.storedCulture}/$needed] Culture · [${ceil((needed - manager.storedCulture) / culture).toInt().coerceAtLeast(0)}] turns".tr()
            else -> "[${manager.storedCulture}/$needed] Culture".tr()
        }

        for (branch in civ.gameInfo.ruleset.policyBranches.values) branchSection(branch)

        val policy = selected
        if (policy != null && !manager.isAdopted(policy.name)) {
            primary(
                if (manager.freePolicies > 0) "Adopt free policy" else "Adopt policy",
                enabled = policy.isPickable(), symbol = "scroll.fill"
            ) { adopt(policy) }
        }
    }

    private fun NativeForm.branchSection(branch: PolicyBranch) {
        val branchAdopted = manager.isAdopted(branch.name)
        val members = branch.policies.filter { it.policyBranchType != PolicyBranchType.BranchComplete }
        val lockedByEra = civ.gameInfo.ruleset.eras[branch.era]!!.eraNumber > civ.getEraNumber()
        section(branch.name, id = "branch.${branch.name}", icon = "PolicyBranchIcons/${branch.name}",
            expanded = branchAdopted || branch.isPickable(),
            footer = if (lockedByEra) "{Unlocked at} {${branch.era}}" else null) {
            if (branchAdopted) {
                val done = if (manager.isAdopted(branch.policies.last().name)) members.size
                    else members.count { manager.isAdopted(it.name) }
                meter("branch.${branch.name}.progress", "Completed", done / members.size.toFloat().coerceAtLeast(1f),
                    value = "$done/${members.size}", color = "#f5c542")
            } else {
                text("branch.${branch.name}.opener", branch.getDescription())
                button("branch.${branch.name}.adopt", "Adopt [${branch.name}]", symbol = "flag.fill", role = "primary",
                    enabled = branch.isPickable(), confirm = "Are you sure you want to adopt [${branch.name}]?") {
                    manager.adopt(branch, false)
                    SoundPlayer.play(UncivSound.Policy)
                }
            }
            for (policy in members.sortedWith(compareBy({ it.row }, { it.column }))) {
                val adopted = manager.isAdopted(policy.name)
                val pickable = policy.isPickable()
                row("policy.${policy.name}", policy.name,
                    // Only what is still missing; an adopted branch with too little culture requires nothing
                    detail = policy.requires.orEmpty().filter { !manager.isAdopted(it) }
                        .takeIf { !adopted && !pickable && it.isNotEmpty() }
                        ?.let { "{Requires} " + it.joinToString { name -> "{$name}" } },
                    icon = icon(policy.name, adopted, pickable),
                    tag = when { adopted -> "Adopted"; pickable -> "Available"; else -> null },
                    tagStyle = if (adopted) "good" else "accent",
                    selected = selected == policy) { selected = policy }
                // Right under the row: a section at the top would be scrolled out of sight
                if (selected == policy) text("policy.${policy.name}.description",
                    // The row above already names it
                    policy.getDescription().lines().dropWhile { it == policy.name.tr() }.joinToString("\n"))
            }
        }
    }

    private fun adopt(policy: Policy) {
        if (!policy.isPickable()) return
        manager.adopt(policy)
        SoundPlayer.play(UncivSound.Policy)
        // Adoption can open another screen (great person pick, victory); leave this one then
        if (UncivGame.Current.screen === screen) {
            if (!manager.canAdoptPolicy()) UncivGame.Current.popScreen()
            else selected = null
        }
    }
}
