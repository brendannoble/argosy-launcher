package com.nendo.argosy.ui.input

import android.view.KeyEvent

class SelectShortcutHandler {
    private enum class State { IDLE, HELD, COMBO_FIRED }

    private var state = State.IDLE
    private var combos: Map<GamepadEvent, GamepadEvent> = emptyMap()
    private val consumedButtons = mutableSetOf<GamepadEvent>()

    fun configure(selectL: String, selectR: String) {
        val updated = buildMap {
            actionEvent(selectL)?.let { put(GamepadEvent.PrevSection, it) }
            actionEvent(selectR)?.let { put(GamepadEvent.NextSection, it) }
        }
        if (combos != updated) reset()
        combos = updated
    }

    fun reset() {
        state = State.IDLE
        consumedButtons.clear()
    }

    fun handle(
        event: GamepadEvent,
        action: Int,
        repeatCount: Int,
        emit: (GamepadEvent) -> Unit
    ): Boolean {
        if (event in consumedButtons) {
            if (action == KeyEvent.ACTION_UP) consumedButtons.remove(event)
            return true
        }
        if (event == GamepadEvent.Select && combos.isNotEmpty()) {
            when (action) {
                KeyEvent.ACTION_DOWN -> if (repeatCount == 0) state = State.HELD
                KeyEvent.ACTION_UP -> {
                    val standalone = state == State.HELD
                    state = State.IDLE
                    if (standalone) emit(GamepadEvent.Select)
                }
            }
            return true
        }
        if (action == KeyEvent.ACTION_DOWN && state != State.IDLE) {
            val shortcut = combos[event] ?: return false
            state = State.COMBO_FIRED
            consumedButtons.add(event)
            if (repeatCount == 0) emit(shortcut)
            return true
        }
        return false
    }

    private fun actionEvent(action: String): GamepadEvent? = when (action) {
        "quick_menu" -> GamepadEvent.LeftStickClick
        "quick_settings" -> GamepadEvent.RightStickClick
        else -> null
    }
}
