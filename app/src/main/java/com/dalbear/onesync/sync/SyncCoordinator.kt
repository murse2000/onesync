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
        /** 이번 폴더 쌍에서 끝난 작업 수. */
        val done: Int = 0,
        /** 이번 폴더 쌍의 전체 작업 수. 0이면 아직 계획 전이다. */
        val total: Int = 0,
        /** 전체 동기화에서 몇 번째 폴더인지(1부터). */
        val pairIndex: Int = 0,
        val pairCount: Int = 0,
    ) {
        val remaining: Int get() = (total - done).coerceAtLeast(0)
        val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun begin(pairCount: Int) {
        _state.value = State(running = true, pairCount = pairCount)
    }

    fun progress(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    /** 끝난 작업 수/전체 작업 수를 갱신한다. */
    fun count(done: Int, total: Int, pairIndex: Int, pairCount: Int) {
        _state.value = _state.value.copy(
            done = done,
            total = total,
            pairIndex = pairIndex,
            pairCount = pairCount,
        )
    }

    fun finish(result: String?) {
        _state.value = State(running = false, result = result)
    }
}
