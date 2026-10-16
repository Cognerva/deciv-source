package com.unciv.ui.nativeui

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText

/**
 * Native Civilopedia: categories as chips, a searchable list of entries, and an entry page that
 * renders the game's own formatted text, whose links lead to other entries.
 */
class CivilopediaPresenter(private val ruleset: Ruleset, category: CivilopediaCategories, link: String) : NativeFormPresenter() {
    private var category = category
    private var entry: String? = null
    private var query = ""
    /** Entries visited through links, for Back */
    private val history = ArrayList<Pair<CivilopediaCategories, String?>>()

    init {
        if (link.isNotEmpty()) select(link, remember = false)
    }

    private val gameInfo get() = UncivGame.Current.gameInfo

    private fun items(category: CivilopediaCategories): List<ICivilopediaText> {
        if (category == CivilopediaCategories.Belief && (ruleset.beliefs.isEmpty() || gameInfo?.isReligionEnabled() == false))
            return emptyList()
        return category.getCategoryIterator(ruleset, gameInfo)
            .filter { it !is IHasUniques || !it.isHiddenFromCivilopedia(gameInfo, ruleset) }
    }

    /** Atlas region for an entry, by the folder its category's pictures live in */
    private fun iconFor(category: CivilopediaCategories?, name: String): String? {
        val folder = when (category) {
            CivilopediaCategories.Building, CivilopediaCategories.Wonder -> "BuildingIcons"
            CivilopediaCategories.Resource -> "ResourceIcons"
            CivilopediaCategories.Improvement -> "ImprovementIcons"
            CivilopediaCategories.Unit -> "UnitIcons"
            CivilopediaCategories.UnitType -> "UnitTypeIcons"
            CivilopediaCategories.Nation -> "NationIcons"
            CivilopediaCategories.Technology -> "TechIcons"
            CivilopediaCategories.Promotion -> return "UnitPromotionIcons/" + name.substringBefore(" (").replace(Regex(" [IV]+$"), "")
            CivilopediaCategories.Policy -> "PolicyIcons"
            CivilopediaCategories.Belief -> "ReligionIcons"
            else -> return null
        }
        return "$folder/$name"
    }

    /** `Category/Entry` as used by formatted-line links */
    private fun iconForLink(link: String): String? {
        val parts = link.split('/', limit = 2)
        if (parts.size < 2) return null
        return iconFor(CivilopediaCategories.fromLink(parts[0]), parts[1])
    }

    private fun select(link: String, remember: Boolean = true) {
        val parts = link.split('/', limit = 2)
        val target = CivilopediaCategories.fromLink(parts[0]) ?: return
        if (remember) history += category to entry
        category = target
        entry = parts.getOrNull(1)
        query = ""
    }

    private fun symbol(category: CivilopediaCategories) = when (category) {
        CivilopediaCategories.Building -> "building.2.fill"
        CivilopediaCategories.Wonder -> "building.columns.fill"
        CivilopediaCategories.Resource -> "diamond.fill"
        CivilopediaCategories.Terrain -> "mountain.2.fill"
        CivilopediaCategories.Improvement -> "hammer.fill"
        CivilopediaCategories.Unit -> "shield.fill"
        CivilopediaCategories.UnitType -> "square.grid.2x2.fill"
        CivilopediaCategories.Nation -> "flag.fill"
        CivilopediaCategories.Personality -> "person.fill"
        CivilopediaCategories.Technology -> "flask.fill"
        CivilopediaCategories.Promotion -> "star.fill"
        CivilopediaCategories.Policy -> "scroll.fill"
        CivilopediaCategories.Belief -> "sparkles"
        CivilopediaCategories.Tutorial -> "lightbulb.fill"
        CivilopediaCategories.Victory -> "trophy.fill"
        CivilopediaCategories.UnitNameGroup -> "textformat"
        CivilopediaCategories.Difficulty -> "dial.medium.fill"
        CivilopediaCategories.Era -> "hourglass"
        CivilopediaCategories.Speed -> "speedometer"
    }

    override fun NativeForm.build() {
        val current = entry?.let { name -> items(category).firstOrNull { (it as INamed).name == name } }
        title = if (current != null) (current as INamed).name else "Civilopedia"
        closeSymbol = if (current != null || history.isNotEmpty()) "chevron.left" else "xmark"
        onClose {
            when {
                history.isNotEmpty() -> history.removeAt(history.lastIndex).let { (c, e) -> category = c; entry = e }
                entry != null -> entry = null
                else -> UncivGame.Current.popScreen()
            }
        }

        if (current != null) {
            subtitle = category.label
            section(id = "entry") {
                markup("entry.text", current.assembleCivilopediaText(ruleset).civilopediaText, ::iconForLink) { link ->
                    if (link.startsWith("http") || link.startsWith("mailto:")) Gdx.net.openURI(link) else select(link)
                }
            }
            return
        }

        header {
            choice("category", "", category.name, CivilopediaCategories.entries.filter { items(it).isNotEmpty() }.map {
                NativeForm.Option(it.name, it.label, symbol = symbol(it))
            }, style = "chips") { name ->
                CivilopediaCategories.entries.firstOrNull { it.name == name }?.let { category = it; query = "" }
            }
            search("search", "Search", query) { query = it }
        }

        val all = items(category)
        val filtered = if (query.isBlank()) all
            else all.filter { (it as INamed).name.tr(hideIcons = true).contains(query.trim(), ignoreCase = true) }
        val collator = UncivGame.Current.settings.getCollatorFromLocale()
        val sorted = filtered.sortedWith(compareBy<ICivilopediaText> { it.getSortGroup(ruleset) }
            .thenBy(collator) { (it as INamed).name.tr(hideIcons = true, hideStats = true) })
        val groups = sorted.groupBy { it.getSubCategory(ruleset) }
        for ((subCategory, entries) in groups) {
            section(subCategory, id = "group.${subCategory ?: "all"}") {
                for (item in entries) {
                    val name = (item as INamed).name
                    val icon = iconFor(category, item.getIconName())
                    row("entry.$name", name, icon = icon?.let { NativeForm.Option(name, name, icon = it) }
                        ?: NativeForm.Option(name, name, symbol = symbol(category))) {
                        entry = name
                    }
                }
            }
        }
    }
}
