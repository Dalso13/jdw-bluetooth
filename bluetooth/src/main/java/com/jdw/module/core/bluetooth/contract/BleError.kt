package com.jdw.module.core.bluetooth.contract

/**
 * 블루투스 에러 타입
 * @property TIMEOUT 연결 시간 초과
 * @property GATT_ERROR 133번 에러 등 GATT 내부 에러
 * @property PERMISSION_DENIED 권한 없음
 * @property DISCONNECTED_BY_DEVICE 상대가 끊음
 */
enum class BleError {
    TIMEOUT,           // 연결 시간 초과
    GATT_ERROR,        // 133번 에러 등 GATT 내부 에러
    PERMISSION_DENIED, // 권한 없음
    DISCONNECTED_BY_DEVICE, // 상대가 끊음
    BLUETOOTH_DISABLED,     // 블루투스 안켜져있음
    SCAN_FAILED             // 스캔 실패함
}