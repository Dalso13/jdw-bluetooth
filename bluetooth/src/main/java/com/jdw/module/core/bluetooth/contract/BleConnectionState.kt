package com.jdw.module.core.bluetooth.contract

/**
 * 블루투스 연결 상태
 * @property Disconnected 초기 상태
 * @property Connecting 연결 시도 중 (GATT 연결)
 * @property Discovering 연결 후 서비스 찾기 (Service Discovery)
 * @property Ready 진짜 통신 가능한 상태 (서비스 찾기 완료)
 * @property Disconnecting 연결 해제 중
 * @property Error 에러 상태를 구체화해야 대응이 가능함
 */
sealed interface BleConnectionState {
    object Disconnected : BleConnectionState // 초기 상태
    object Connecting : BleConnectionState   // 연결 시도 중 (GATT 연결)
    object Discovering : BleConnectionState  // 연결 후 서비스 찾기 (Service Discovery)
    object Ready : BleConnectionState        // 진짜 통신 가능한 상태 (서비스 찾기 완료)
    object Disconnecting : BleConnectionState // 연결 해제 중

    // 에러 상태
    data class Error(val type: BleError, val msg: String) : BleConnectionState
}