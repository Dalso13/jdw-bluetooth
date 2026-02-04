package com.jdw.module.core.bluetooth.contract

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 블루투스 클라이언트
 * @property scanState 스캔 상태
 * @property connectionState 연결 상태
 * @property notifyFlow 데이터 수신 Flow (Notification/Indication)
 * @property startScan 스캔 시작
 * @property stopScan 스캔 중지
 * @property connect 연결 시작
 * @property disconnect 연결 해제
 * @property writeCharacteristic 데이터 쓰기
 * @property readCharacteristic 데이터 읽기
 * @property close 종료
 */
interface BleClient {

    // 현재 스캔 상태
    val scanState: StateFlow<BleScanState>

    // 현재 연결 상태
    val connectionState: StateFlow<BleConnectionState>

    // 데이터 수신 Flow (Notification/Indication)
    // Pair<CharacteristicUUID, Data>
    val notifyFlow: SharedFlow<Pair<String, ByteArray>>


    // 스캔 시작
    fun startScan()

    // 스캔 중지
    fun stopScan()

    // 연결 시작
    fun connect(device: BluetoothDevice)

    // 데이터 통신
    suspend fun writeCharacteristic(
        characteristicUuid: String,
        data: ByteArray,
        serviceUuid: String?,
        writeType: Int?
    ): Result<Unit>

    // 데이터 읽기
    suspend fun readCharacteristic(characteristicUuid: String): Result<ByteArray>

    // 연결 해제
    fun disconnect()

    // 종료
    fun close()
}