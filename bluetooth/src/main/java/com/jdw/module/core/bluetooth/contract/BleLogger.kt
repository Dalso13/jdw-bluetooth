package com.jdw.module.core.bluetooth.contract

import android.util.Log

/**
 * BLE 라이브러리 전용 로거
 * Config.isDebugMode에 따라 로깅 여부 결정
 */
internal object BleLogger {

    private const val BASE_TAG = "BLE-Library"

    // 로깅 활성화 여부 (Config에서 주입받음)
    var isEnabled: Boolean = false

    /**
     * 컴포넌트별 로그 레벨
     */
    enum class Component(val tag: String) {
        CLIENT("$BASE_TAG-Client"),
        SCANNER("$BASE_TAG-Scanner"),
        GATT("$BASE_TAG-Gatt"),
        PERMISSION("$BASE_TAG-Permission"),
        QUEUE("$BASE_TAG-Queue")
    }

    // Verbose (상세 정보)
    fun v(component: Component, message: String) {
        if (isEnabled) {
            Log.v(component.tag, message)
        }
    }

    // Debug (디버그 정보)
    fun d(component: Component, message: String) {
        if (isEnabled) {
            Log.d(component.tag, message)
        }
    }

    // Info (일반 정보)
    fun i(component: Component, message: String) {
        if (isEnabled) {
            Log.i(component.tag, message)
        }
    }

    // Warning (경고)
    fun w(component: Component, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Log.w(component.tag, message, throwable)
            } else {
                Log.w(component.tag, message)
            }
        }
    }

    // Error (에러)
    fun e(component: Component, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Log.e(component.tag, message, throwable)
            } else {
                Log.e(component.tag, message)
            }
        }
    }

    // 상태 변화 로그 (특별 포맷)
    fun state(component: Component, from: Any?, to: Any) {
        if (isEnabled) {
            val message = if (from != null) {
                "State: $from → $to"
            } else {
                "State: → $to"
            }
            Log.i(component.tag, "🔄 $message")
        }
    }

    // 데이터 로그 (ByteArray를 Hex로 출력)
    fun data(component: Component, label: String, data: ByteArray) {
        if (isEnabled) {
            val hex = data.joinToString(" ") { "%02X".format(it) }
            Log.d(component.tag, "📦 $label: [$hex] (${data.size} bytes)")
        }
    }

    // 성공 로그
    fun success(component: Component, message: String) {
        if (isEnabled) {
            Log.i(component.tag, "✅ $message")
        }
    }

    // 실패 로그
    fun failure(component: Component, message: String, error: Throwable? = null) {
        if (isEnabled) {
            val msg = "❌ $message"
            if (error != null) {
                Log.e(component.tag, msg, error)
            } else {
                Log.e(component.tag, msg)
            }
        }
    }
}

