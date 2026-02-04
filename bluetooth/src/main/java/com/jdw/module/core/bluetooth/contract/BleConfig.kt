package com.jdw.module.core.bluetooth.contract

import android.bluetooth.le.ScanSettings

/**
 * 블루투스 설정
 * @property serviceUuid 찾고자 하는 서비스 UUID
 * @property enableNotificationOnConnect Notify 기능 활성화 여부
 * @property notifyCharUuid 값을 받을(Notify) 특성 UUID
 * @property scanTimeoutMillis 스캔을 몇 초 동안 할지
 * @property isDebugMode 로그를 킬지 말지
 * @property shouldAutoConnect 자동 연결 여부
 * @property connectionTimeoutMillis 연결 타임아웃 시간 (밀리초)
 * @property discoveryDelayMillis Service Discovery 전 딜레이 (안정성 향상)
 * @property scanMode 스캔 모드 설정
 */
interface BleConfig {

    // 찾고자 하는 서비스 UUID
    val serviceUuid: String

    // Notify 기능 활성화 여부
    val enableNotificationOnConnect: Boolean

    // 값을 받을(Notify) 특성 UUID
    val notifyCharUuid: String?

    // 스캔을 몇 초 동안 할지
    val scanTimeoutMillis: Long

    // 로그를 킬지 말지 (디버깅용)
    val isDebugMode: Boolean

    // 자동 연결 여부
    val shouldAutoConnect: Boolean

    // 연결 타임아웃 시간 (밀리초)
    val connectionTimeoutMillis: Long
        get() = 10_000L  // 기본 10초

    // Service Discovery 전 딜레이 (안정성 향상)
    val discoveryDelayMillis: Long
        get() = 500L  // 기본 0.5초

    // 스캔 모드 설정을 외부에서 주입 가능
    val scanMode: Int
        get() = ScanSettings.SCAN_MODE_BALANCED
}