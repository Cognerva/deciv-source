package com.unciv.ui.nativeui

import com.badlogic.gdx.graphics.Color

/**
 * Minimal JSON writer for native UI snapshots.
 *
 * Deliberately reflection-free: the snapshots cross into RoboVM/Swift, and reflective
 * serialization would need extra tree-shaker rules for every model class.
 */
internal class NativeUiJson {
    private val out = StringBuilder()
    private val needsComma = ArrayDeque<Boolean>()
    /** A key was just written, so the next value belongs to it and takes no comma */
    private var afterKey = false

    fun obj(build: NativeUiJson.() -> Unit): NativeUiJson {
        separator()
        out.append('{')
        needsComma.addLast(false)
        build()
        needsComma.removeLast()
        out.append('}')
        return this
    }

    fun obj(key: String, build: NativeUiJson.() -> Unit) {
        key(key)
        obj(build)
    }

    fun array(key: String, build: NativeUiJson.() -> Unit) {
        key(key)
        separator()
        out.append('[')
        needsComma.addLast(false)
        build()
        needsComma.removeLast()
        out.append(']')
    }

    fun put(key: String, value: String?) {
        key(key)
        afterKey = false
        if (value == null) out.append("null") else string(value)
    }

    fun put(key: String, value: Boolean) {
        key(key)
        afterKey = false
        out.append(value)
    }

    fun put(key: String, value: Int) {
        key(key)
        afterKey = false
        out.append(value)
    }

    fun put(key: String, value: Float) {
        key(key)
        afterKey = false
        out.append(if (value.isFinite()) value.toString() else "0")
    }

    fun put(key: String, value: Color) = put(key, "#" + value.toString().take(6))

    fun put(key: String, value: Double) {
        key(key)
        afterKey = false
        out.append(if (value.isFinite()) value.toString() else "0")
    }

    /** A flat array of numbers, written compactly: whole numbers without a fraction, others to one decimal */
    fun numbers(key: String, values: FloatArray, count: Int = values.size) {
        key(key)
        afterKey = false
        out.append('[')
        for (i in 0 until count) {
            if (i > 0) out.append(',')
            val value = values[i]
            val rounded = Math.round(value * 10f)
            if (rounded % 10 == 0) out.append(rounded / 10) else out.append(rounded / 10f)
        }
        out.append(']')
    }

    /** An array of strings */
    fun strings(key: String, values: Iterable<String>) = array(key) {
        for (value in values) value(value)
    }

    /** A bare string element inside an [array] */
    fun value(value: String) {
        separator()
        string(value)
    }

    private fun key(key: String) {
        separator()
        string(key)
        out.append(':')
        afterKey = true
    }

    private fun separator() {
        if (afterKey) {
            afterKey = false
            return
        }
        if (needsComma.isEmpty()) return
        if (needsComma.last()) out.append(',')
        needsComma.removeLast()
        needsComma.addLast(true)
    }

    private fun string(value: String) {
        out.append('"')
        for (char in value) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append("\\u%04x".format(char.code))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    override fun toString() = out.toString()

    companion object {
        /**
         * Unciv embeds stat and turn glyphs in the Unicode private-use area; they only exist in
         * the Scene2D font. Native text drops them and collapses the spacing they leave behind.
         */
        fun plainText(text: String): String = text
            .filterNot { it in ''..'' }
            .replace(Regex("[\\u2000-\\u200B]+"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }
}
