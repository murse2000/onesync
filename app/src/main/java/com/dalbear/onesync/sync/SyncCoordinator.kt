package com.dalbear.onesync.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 진행 중인 동기화 상태를 UI와 서비스가 공유한다.
 * 서비스가 실제 작업을 소유하고, 화면은 이 상태만 관찰한다.
 */
object SyncCoordinator {

    data class State(
        val running: Boolean = false,
        val message: String? = null,
        val result: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun begin() {
        _state.value = State(running = true)
    }

    fun progress(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    fun finish(result: String?) {
        _state.value = State(running = false, result = result)
    }
}
