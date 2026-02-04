package com.jdw.bluetooth.test

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdw.module.core.bluetooth.contract.BleClient
import com.jdw.module.core.bluetooth.contract.BleConnectionState
import com.jdw.module.core.bluetooth.contract.BleScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BleViewModel(private val bleClient: BleClient) : ViewModel() {
    val scanState = bleClient.scanState
        .stateIn(viewModelScope, SharingStarted.Lazily, BleScanState.Idle)

    // 연결 상태
    val connectionState = bleClient.connectionState
        .stateIn(viewModelScope, SharingStarted.Lazily, BleConnectionState.Disconnected)

    // 수신 데이터
    private val _receivedData = MutableStateFlow<String>("")
    val receivedData: StateFlow<String> = _receivedData.asStateFlow()

    // 심박수 (예시)
    private val _heartRate = MutableStateFlow<Int>(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    // 배터리 레벨
    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    init {
        // Notification 자동 구독
        viewModelScope.launch {
            bleClient.notifyFlow.collect { (uuid, data) ->
                Log.d("BLE", "📨 Notification 수신 from $uuid: ${data.toHexString()}")
                _receivedData.value = "Notification from $uuid: ${data.toHexString()}"

                // 심박수 파싱 (예시)
                if (data.isNotEmpty()) {
                    val bpm = data[1].toInt() and 0xFF
                    _heartRate.value = bpm
                }
            }
        }
    }

    // 1. 스캔 시작
    fun startScan() {
        Log.d("BLE", "🔍 스캔 시작")
        bleClient.startScan()
    }

    // 2. 스캔 중지
    fun stopScan() {
        Log.d("BLE", "⏹️ 스캔 중지")
        bleClient.stopScan()
    }

    // 3. 연결
    fun connect(device: BluetoothDevice) {
        try {
            Log.d("BLE", "🔗 연결 시도: ${device.name} (${device.address})")
            bleClient.connect(device)
        } catch (e: SecurityException) {
            Log.e("BLE", "❌ 권한 없음: ${e.message}")
        }
    }

    // 4. 연결 해제
    fun disconnect() {
        Log.d("BLE", "🔌 연결 해제")
        bleClient.disconnect()
    }

    // 5. 데이터 쓰기 (Write)
    fun sendData(text: String) {
        viewModelScope.launch {
            if (connectionState.value !is BleConnectionState.Ready) {
                Log.e("BLE", "❌ Not connected!")
                _receivedData.value = "Error: Not connected"
                return@launch
            }

            val data = text.toByteArray()
            Log.d("BLE", "📤 데이터 전송: $text")

            bleClient.writeCharacteristic(
                characteristicUuid = "00002a39-0000-1000-8000-00805f9b34fb",
                data = data,
                serviceUuid = null,
                writeType = null
            ).onSuccess {
                Log.d("BLE", "✅ Write 성공!")
                _receivedData.value = "Write 성공: $text"
            }.onFailure { error ->
                Log.e("BLE", "❌ Write 실패: ${error.message}")
                _receivedData.value = "Write 실패: ${error.message}"
            }
        }
    }

    // 6. 데이터 읽기 (Read)
    fun readSensorValue() {
        viewModelScope.launch {
            if (connectionState.value !is BleConnectionState.Ready) {
                Log.e("BLE", "❌ Not connected!")
                return@launch
            }

            Log.d("BLE", "📊 센서 값 읽기")

            bleClient.readCharacteristic(
                characteristicUuid = "00002a38-0000-1000-8000-00805f9b34fb"
            ).onSuccess { data ->
                Log.d("BLE", "✅ 센서 값: ${data.toHexString()}")
                _receivedData.value = "센서: ${data.toHexString()}"
            }.onFailure { error ->
                Log.e("BLE", "❌ Read 실패: ${error.message}")
                _receivedData.value = "센서 읽기 실패: ${error.message}"
            }
        }
    }

    // 정리
    override fun onCleared() {
        super.onCleared()
        Log.d("BLE", "🧹 ViewModel 정리")
        bleClient.close()
    }
}

// ByteArray를 Hex String으로 변환
private fun ByteArray.toHexString(): String {
    return joinToString(" ") { "%02X".format(it) }
}
