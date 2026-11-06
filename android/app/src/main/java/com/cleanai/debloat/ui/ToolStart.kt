package com.cleanai.debloat.ui

/**
 * What a tool should do as soon as it opens, when another screen sends you there for one job
 * (PDF Studio's "New document" goes straight to a blank Word file). Read once, then forgotten.
 */
object ToolStart {
    enum class Action { NEW, OPEN }

    private var pending: Pair<String, Action>? = null

    fun set(toolId: String, action: Action) { pending = toolId to action }

    fun take(toolId: String): Action? = pending?.takeIf { it.first == toolId }?.second.also { if (it != null) pending = null }
}
