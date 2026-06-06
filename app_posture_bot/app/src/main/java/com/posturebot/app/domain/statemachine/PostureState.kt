package com.posturebot.app.domain.statemachine

/**
 * Posture state output from the state machine.
 */
sealed class PostureState {
    data object Idle : PostureState()
    data object Calibrating : PostureState()
    data object Good : PostureState()
    data object Warning : PostureState()
    data object Bad : PostureState()
    data object Stopped : PostureState()
}
