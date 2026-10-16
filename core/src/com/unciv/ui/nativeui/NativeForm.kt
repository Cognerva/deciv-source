package com.unciv.ui.nativeui

import com.unciv.models.translations.tr
import com.unciv.ui.nativeui.NativeUiJson.Companion.plainText

/**
 * A settings-style native screen built from sections of toggles, pickers, sliders and buttons.
 *
 * [build] runs for every snapshot and each control registers its handler under its id, so an
 * action always reaches the handler belonging to the latest snapshot. Ids are stable names such
 * as `difficulty` or `player.2.civ`; the platform sends `set:<id>:<value>` or `tap:<id>`.
 * Labels are translated here, so callers pass the game's English keys.
 */
abstract class NativeFormPresenter : NativeScreenPresenter {
    override val kind = "form"
    private val handlers = HashMap<String, (String) -> Unit>()

    protected abstract fun NativeForm.build()

    override fun snapshotJson(): String {
        handlers.clear()
        val form = NativeForm(handlers)
        form.build()
        return form.toJson()
    }

    override fun perform(id: String) {
        val parts = id.split(':', limit = 3)
        when (parts[0]) {
            "set" -> if (parts.size == 3) handlers[parts[1]]?.invoke(parts[2])
            "tap" -> if (parts.size >= 2) handlers[parts[1]]?.invoke("")
        }
    }
}

class NativeForm internal constructor(private val handlers: MutableMap<String, (String) -> Unit>) {
    class Option(
        val id: String,
        val label: String,
        val detail: String? = null,
        /** Atlas region, e.g. `NationIcons/Rome` */
        val icon: String? = null,
        /** SF Symbol name */
        val symbol: String? = null,
        /** Badge colours behind and of the icon, `#rrggbb` */
        val background: String? = null,
        val tint: String? = null,
    )

    /** One line of a [Section.chart]: [points] are turn and value pairs */
    class ChartSeries(val name: String, val color: String, val points: List<Pair<Int, Int>>, val highlighted: Boolean)

    var title = ""
    var subtitle: String? = null
    /** Leading button; the platform sends `tap:close` */
    var closeSymbol = "xmark"
    private var primary: Triple<String, Boolean, String?>? = null
    /** Shown over the form with a spinner while a long task runs */
    var busy: String? = null
    private var alert: Pair<String, String>? = null
    private val catalogs = LinkedHashMap<String, List<Option>>()
    private val sections = ArrayList<Section>()
    private var header: Section? = null

    fun onClose(action: () -> Unit) {
        handlers["close"] = { action() }
    }

    /** The prominent button at the bottom, e.g. "Start game!" */
    fun primary(label: String, enabled: Boolean = true, symbol: String? = null, action: () -> Unit) {
        primary = Triple(label, enabled, symbol)
        handlers["primary"] = { action() }
    }

    /** A message the player must acknowledge; [onDismiss] runs when they do. */
    fun alert(title: String, message: String, onDismiss: () -> Unit) {
        alert = title to message
        handlers["alert"] = { onDismiss() }
    }

    /** Options shared by several pickers, such as the nation list, sent only once */
    fun catalog(name: String, options: List<Option>) {
        catalogs[name] = options
    }

    /** Controls shown above the sections without a card, e.g. preset tiles */
    fun header(build: Section.() -> Unit) {
        header = Section(null, null, null, null, null).apply(build)
    }

    /**
     * @param expanded null for a plain section, otherwise a disclosure that starts open or closed
     * @param icon atlas region shown next to the title
     */
    fun section(
        title: String? = null, footer: String? = null, expanded: Boolean? = null, id: String? = null,
        icon: String? = null, build: Section.() -> Unit
    ) {
        sections += Section(title, footer, expanded, id ?: title, icon).apply(build)
    }

    inner class Section internal constructor(
        val title: String?,
        val footer: String?,
        val expanded: Boolean?,
        val id: String?,
        val icon: String?,
    ) {
        internal val items = ArrayList<NativeUiJson.() -> Unit>()

        private fun item(type: String, id: String, label: String, detail: String?, enabled: Boolean, extra: NativeUiJson.() -> Unit) {
            require(':' !in id) { "Form ids may not contain ':'" }
            items.add {
                obj {
                    put("type", type)
                    put("id", id)
                    put("label", plainText(label.tr()))
                    put("detail", detail?.let { plainText(it.tr()) })
                    put("enabled", enabled)
                    this.extra()
                }
            }
        }

        fun toggle(id: String, label: String, value: Boolean, detail: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
            item("toggle", id, label, detail, enabled) { put("on", value) }
            handlers[id] = { onChange(it == "true") }
        }

        /**
         * One choice out of [options]. [style] is `menu` (a pop-up menu), `list` (a pushed list,
         * for long or illustrated lists) or `segmented`. Pass [catalog] instead of options to use a shared list.
         */
        fun choice(
            id: String, label: String, value: String, options: List<Option> = emptyList(),
            style: String = "menu", catalog: String? = null, detail: String? = null,
            icon: String? = null, enabled: Boolean = true, onChange: (String) -> Unit
        ) {
            item("choice", id, label, detail, enabled) {
                put("value", value)
                put("style", style)
                put("catalog", catalog)
                put("icon", icon)
                if (catalog == null) array("options") { for (option in options) option.write(this) }
            }
            handlers[id] = onChange
        }

        /** Several of [options] at once; the platform sends the toggled option's id */
        fun multi(id: String, label: String, selected: Collection<String>, options: List<Option>, detail: String? = null, enabled: Boolean = true, onToggle: (String) -> Unit) {
            item("multi", id, label, detail, enabled) {
                strings("selected", selected)
                array("options") { for (option in options) option.write(this) }
            }
            handlers[id] = onToggle
        }

        /** @param format `int`, `percent` (value 0…1) or `decimal` */
        fun slider(
            id: String, label: String, value: Float, min: Float, max: Float, step: Float,
            format: String = "int", detail: String? = null, enabled: Boolean = true, onChange: (Float) -> Unit
        ) {
            item("slider", id, label, detail, enabled) {
                put("number", value)
                put("min", min)
                put("max", max)
                put("step", step)
                put("format", format)
            }
            handlers[id] = { it.toFloatOrNull()?.let(onChange) }
        }

        fun stepper(id: String, label: String, value: Int, min: Int, max: Int, detail: String? = null, enabled: Boolean = true, onChange: (Int) -> Unit) {
            item("stepper", id, label, detail, enabled) {
                put("number", value.toFloat())
                put("min", min.toFloat())
                put("max", max.toFloat())
                put("step", 1f)
                put("format", "int")
            }
            handlers[id] = { it.toFloatOrNull()?.let { value -> onChange(value.toInt()) } }
        }

        /**
         * @param role `default`, `primary` or `destructive`
         * @param confirm when set, the platform asks this question before sending the tap
         */
        fun button(
            id: String, label: String, symbol: String? = null, role: String = "default",
            detail: String? = null, confirm: String? = null, enabled: Boolean = true, action: () -> Unit
        ) {
            item("button", id, label, detail, enabled) {
                put("symbol", symbol)
                put("role", role)
                put("confirm", confirm?.let { plainText(it.tr()) })
            }
            handlers[id] = { action() }
        }

        /** A read-only label and value */
        fun text(id: String, label: String, value: String? = null, detail: String? = null, symbol: String? = null) {
            item("text", id, label, detail, true) {
                put("value", value?.let { plainText(it.tr()) })
                put("symbol", symbol)
            }
        }

        /**
         * A list row with an icon badge, e.g. a policy, a city or a civilization.
         * @param tag short status shown on the trailing side; [tagStyle] is `good`, `accent`, `muted` or `bad`
         * @param value trailing text, e.g. a number
         * @param action runs on tap; without one the row is static
         */
        fun row(
            id: String, label: String, detail: String? = null, value: String? = null,
            icon: NativeForm.Option? = null, tag: String? = null, tagStyle: String = "muted",
            selected: Boolean = false, enabled: Boolean = true, translate: Boolean = true, action: (() -> Unit)? = null
        ) {
            require(':' !in id) { "Form ids may not contain ':'" }
            items.add {
                obj {
                    put("type", "row")
                    put("id", id)
                    // Stat glyphs would repeat the row's icon, e.g. on "Gold (30)"
                    put("label", plainText(if (translate) label.tr(hideIcons = true, hideStats = true) else label))
                    put("detail", detail?.let { plainText(if (translate) it.tr() else it) })
                    put("enabled", enabled)
                    put("value", value?.let { plainText(it) })
                    put("icon", icon?.icon)
                    put("symbol", icon?.symbol)
                    put("background", icon?.background)
                    put("tint", icon?.tint)
                    put("tag", tag?.let { plainText(it.tr()) })
                    put("style", tagStyle)
                    put("on", selected)
                    put("role", if (action != null) "action" else "static")
                }
            }
            if (action != null) handlers[id] = { action() }
        }

        /** A search field; the platform sends the text as the player types */
        fun search(id: String, placeholder: String, value: String, onChange: (String) -> Unit) {
            item("search", id, placeholder, null, true) { put("value", value) }
            handlers[id] = onChange
        }

        /**
         * Rich text from the game's formatted lines (Civilopedia entries): headers, separators,
         * colours, indentation and links. A tapped link arrives at [onLink].
         */
        fun markup(id: String, lines: List<com.unciv.ui.screens.civilopediascreen.FormattedLine>, iconFor: (String) -> String?, onLink: (String) -> Unit) {
            item("markup", id, "", null, true) {
                array("lines") {
                    for (line in lines) obj {
                        put("text", plainText(line.text.tr()))
                        put("header", line.header)
                        put("indent", line.indent)
                        put("separator", line.separator)
                        put("starred", line.starred)
                        put("centered", line.centered)
                        put("color", line.color.takeIf { it.isNotEmpty() })
                        put("link", line.link.takeIf { it.isNotEmpty() })
                        put("external", line.linkType == com.unciv.ui.screens.civilopediascreen.FormattedLine.LinkType.External)
                        put("icon", (line.icon.ifEmpty { if (line.linkType == com.unciv.ui.screens.civilopediascreen.FormattedLine.LinkType.Internal) line.link else "" })
                            .takeIf { it.isNotEmpty() }?.let(iconFor))
                    }
                }
            }
            handlers[id] = onLink
        }

        /** A text field; the platform sends the text when editing ends */
        fun field(
            id: String, label: String, value: String, placeholder: String? = null, detail: String? = null,
            enabled: Boolean = true, onSubmit: (String) -> Unit
        ) {
            item("field", id, label, detail, enabled) {
                put("value", value)
                put("placeholder", placeholder?.let { plainText(it.tr(hideIcons = true)) })
            }
            handlers[id] = onSubmit
        }

        /** A line chart over turns, e.g. each civilization's score */
        fun chart(id: String, series: List<ChartSeries>) {
            item("chart", id, "", null, true) {
                obj("chart") {
                    array("series") {
                        for (line in series) obj {
                            put("name", plainText(line.name.tr()))
                            put("color", line.color)
                            put("highlighted", line.highlighted)
                            numbers("points", line.points.flatMap { listOf(it.first.toFloat(), it.second.toFloat()) }.toFloatArray())
                        }
                    }
                }
            }
        }

        /**
         * The world map replayed turn by turn. [tiles] holds x, y and a [palette] index per tile;
         * [owners] are pairs of outer and inner palette indices; [changes] are turn, tile index,
         * owner index (-1 for none) and city type (0 none, 1 city, 2 capital).
         */
        fun replay(
            id: String, width: Float, height: Float, tileSize: Float, palette: Collection<String>,
            tiles: FloatArray, owners: IntArray, changes: IntArray, firstTurn: Int, years: List<String>
        ) {
            item("replay", id, "", null, true) {
                obj("replay") {
                    put("width", width)
                    put("height", height)
                    put("tileSize", tileSize)
                    strings("palette", palette)
                    numbers("tiles", tiles)
                    numbers("owners", FloatArray(owners.size) { owners[it].toFloat() })
                    numbers("changes", FloatArray(changes.size) { changes[it].toFloat() })
                    put("firstTurn", firstTurn)
                    strings("years", years)
                }
            }
        }

        /** A labelled progress bar; [fraction] is 0…1 */
        fun meter(id: String, label: String, fraction: Float, value: String? = null, detail: String? = null, color: String? = null) {
            item("meter", id, label, detail, true) {
                put("number", fraction.coerceIn(0f, 1f))
                put("value", value?.let { plainText(it) })
                put("tint", color)
            }
        }

        /**
         * Large selectable tiles, e.g. game presets; [value] is the selected id or "".
         * [style] `icons` lays the options out as a grid of pictures, e.g. app icons.
         */
        fun tiles(id: String, value: String, options: List<Option>, style: String? = null, onChange: (String) -> Unit) {
            item("tiles", id, "", null, true) {
                put("value", value)
                put("style", style)
                array("options") { for (option in options) option.write(this) }
            }
            handlers[id] = onChange
        }

        /**
         * A player slot: nation from [catalog], a Human/AI switch and a remove button.
         * Sends `set:<id>.civ:<nation>`, `tap:<id>.type` and `tap:<id>.remove`.
         */
        fun player(
            id: String, nation: Option, typeLabel: String, isHuman: Boolean, catalog: String,
            canChangeType: Boolean, canRemove: Boolean, enabled: Boolean = true,
            onNation: (String) -> Unit, onType: () -> Unit, onRemove: () -> Unit
        ) {
            item("player", id, nation.label, nation.detail, enabled) {
                put("value", nation.id)
                put("icon", nation.icon)
                put("background", nation.background)
                put("tint", nation.tint)
                put("catalog", catalog)
                put("tag", plainText(typeLabel.tr()))
                put("on", isHuman)
                put("canChangeType", canChangeType)
                put("canRemove", canRemove)
            }
            handlers["$id.civ"] = onNation
            handlers["$id.type"] = { onType() }
            handlers["$id.remove"] = { onRemove() }
        }
    }

    private fun Option.write(json: NativeUiJson) = json.obj {
        put("id", id)
        put("label", plainText(label.tr(hideIcons = true)))
        put("detail", detail?.let { plainText(it.tr()) })
        put("icon", icon)
        put("symbol", symbol)
        put("background", background)
        put("tint", tint)
    }

    private fun Section.write(json: NativeUiJson) = json.obj {
        put("id", id ?: "")
        put("title", title?.let { plainText(it.tr(hideIcons = true, hideStats = true)) })
        put("icon", icon)
        put("footer", footer?.let { plainText(it.tr()) })
        put("collapsible", expanded != null)
        put("expanded", expanded ?: true)
        array("items") { for (writeItem in items) writeItem(this) }
    }

    internal fun toJson(): String = NativeUiJson().obj {
        put("title", plainText(title.tr()))
        put("subtitle", subtitle?.let { plainText(it.tr()) })
        put("closeSymbol", closeSymbol)
        put("hasClose", "close" in handlers)
        primary?.let { (label, enabled, symbol) ->
            obj("primary") {
                put("label", plainText(label.tr()))
                put("enabled", enabled)
                put("symbol", symbol)
            }
        }
        put("busy", busy?.let { plainText(it.tr()) })
        alert?.let { (title, message) ->
            obj("alert") {
                put("title", plainText(title.tr()))
                put("message", plainText(message.tr()))
            }
        }
        obj("catalogs") {
            for ((name, options) in catalogs) array(name) { for (option in options) option.write(this) }
        }
        header?.let { header -> obj("header") { array("items") { for (writeItem in header.items) writeItem(this) } } }
        array("sections") { for (section in sections) section.write(this) }
    }.toString()
}
