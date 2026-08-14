package com.fitbalance.app.ui.screens

import android.os.Build

/**
 * 카카오 지도를 쓸 수 있는지 여부. 앱 시작 시 한 번 정해진다.
 *
 * 카카오 지도 SDK는 ARM(armeabi-v7a, arm64-v8a) 네이티브 라이브러리만 담고 있어
 * x86_64 에뮬레이터에서는 초기화 단계에서 실패한다. 실기기에서는 정상 동작한다.
 * 지도 화면은 이 값이 false면 자체 도식 지도로 그린다.
 */
object KakaoMapState {

    var available: Boolean = false
        private set

    /** 못 쓰는 이유. 화면에 그대로 보여 준다. */
    var reason: String? = null
        private set

    fun markAvailable() {
        available = true
        reason = null
    }

    fun markUnavailable(error: Throwable?) {
        available = false
        reason = describe(error)
    }

    fun markUnavailable(message: String) {
        available = false
        reason = message
    }

    private fun describe(error: Throwable?): String {
        val msg = error?.message.orEmpty()
        val isEmulatorAbi = msg.contains("x86") ||
            Build.SUPPORTED_ABIS.none { it.startsWith("arm") }
        return when {
            isEmulatorAbi ->
                "카카오 지도는 ARM 기기에서만 동작합니다. x86 에뮬레이터에서는 간이 지도로 표시합니다"
            msg.isNotBlank() -> msg
            else -> "지도를 초기화하지 못했습니다"
        }
    }
}
