package com.jdw.module.core.bluetooth.contract

import android.bluetooth.le.ScanResult

/**
 * 블루투스 스캔 상태
 * @property Idle 초기 상태
 * @property Stopped 스캔 중지됨 (타임아웃 또는 사용자 요청)
 * @property Scanning 스캔 중
 *
 */
sealed interface BleScanState {

    // --- 대기 / 종료 상태 ---
    object Idle : BleScanState
    object Stopped : BleScanState

    // --- 진행 상태 ---
    data class Scanning(val results: List<ScanResult>) : BleScanState

    // 에러 처리
    data class Error(val type: BleError, val message: String?) : BleScanState
}