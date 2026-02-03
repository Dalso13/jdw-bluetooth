package com.jdw.module.core.bluetooth.internal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.jdw.module.core.bluetooth.contract.BleConfig
import com.jdw.module.core.bluetooth.contract.BleError
import com.jdw.module.core.bluetooth.contract.BleLogger
import com.jdw.module.core.bluetooth.contract.BlePermissionChecker
import com.jdw.module.core.bluetooth.contract.BleScanState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BleScanner (
    val config: BleConfig,
    val permissionChecker: BlePermissionChecker,
    val bluetoothManager: BluetoothManager,
) {

    init {
        // Logger 초기화
        BleLogger.isEnabled = config.isDebugMode
        BleLogger.i(BleLogger.Component.SCANNER, "Scanner initialized")
    }

    // 어뎁터
    private val adapter by lazy { bluetoothManager.adapter }

    // 스캐너
    private val scanner by lazy { adapter.bluetoothLeScanner }

    // 내부 상태 관리용 Flow
    private val _scanState = MutableStateFlow<BleScanState>(BleScanState.Idle)
    val scanState: StateFlow<BleScanState> = _scanState.asStateFlow()

    // 스캔 결과 누적용 리스트 (Thread-Safe, Key: MAC Address)
    private val scannedDevices = ConcurrentHashMap<String, ScanResult>()

    // 타임아웃 처리를 위한 Job
    private var scanJob: Job? = null

    // 내부 스코프
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 스캔 콜백 정의
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { newResult ->
                val deviceInfo = try {
                    "${newResult.device.name ?: "Unknown"} (${newResult.device.address})"
                } catch (e: SecurityException) {
                    newResult.device.address
                }
                BleLogger.v(BleLogger.Component.SCANNER, "Device found: $deviceInfo RSSI: ${newResult.rssi}")

                // O(1) 업데이트
                scannedDevices[newResult.device.address] = newResult
                // 상태 업데이트
                _scanState.value = BleScanState.Scanned(scannedDevices.values.toList())
            }
        }

        override fun onScanFailed(errorCode: Int) {
            BleLogger.failure(BleLogger.Component.SCANNER, "Scan failed with code: $errorCode")
            _scanState.value = BleScanState.Error(BleError.SCAN_FAILED, "Scan failed code: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        BleLogger.d(BleLogger.Component.SCANNER, "startScan() called")

        if (!permissionChecker.hasScanPermission()) {
            BleLogger.w(BleLogger.Component.SCANNER, "Scan permission denied")
            _scanState.value = BleScanState.Error(BleError.PERMISSION_DENIED, "Permission denied")
            return
        }

        if (adapter == null || !adapter.isEnabled) {
            BleLogger.w(BleLogger.Component.SCANNER, "Bluetooth is disabled")
            _scanState.value = BleScanState.Error(BleError.BLUETOOTH_DISABLED, "Bluetooth disabled")
            return
        }

        // 기존 리스트 초기화
        scannedDevices.clear()
        BleLogger.state(BleLogger.Component.SCANNER, _scanState.value, BleScanState.Scanning)
        _scanState.value = BleScanState.Scanning

        // 스캔 필터 설정
        val filters = mutableListOf<ScanFilter>()
        if (config.serviceUuid.isNotEmpty()) {
            filters.add(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(config.serviceUuid))
                    .build()
            )
            BleLogger.d(BleLogger.Component.SCANNER, "Filter applied: ${config.serviceUuid}")
        }

        // 스캔 세팅
        val settings = ScanSettings.Builder()
            .setScanMode(config.scanMode)
            .build()

        BleLogger.d(BleLogger.Component.SCANNER, "Scan mode: ${config.scanMode}")

        // 스캔 시작
        try {
            scanner.startScan(filters, settings, scanCallback)
            BleLogger.success(BleLogger.Component.SCANNER, "Scan started successfully")

            // 타임아웃 설정 적용
            if (config.scanTimeoutMillis > 0) {
                scanJob?.cancel()
                scanJob = scope.launch {
                    delay(config.scanTimeoutMillis)
                    BleLogger.i(BleLogger.Component.SCANNER, "Scan timeout reached (${config.scanTimeoutMillis}ms)")
                    stopScan()
                }
            }
        } catch (e: Exception) {
            BleLogger.failure(BleLogger.Component.SCANNER, "Scan start failed", e)
            _scanState.value = BleScanState.Error(BleError.SCAN_FAILED, e.message ?: "Unknown error")
        }
    }

    fun stopScan() {
        BleLogger.d(BleLogger.Component.SCANNER, "stopScan() called")

        // 권한 없으면 중지 명령도 수행 불가할 수 있으나, 상태 변경은 가능
        if (!permissionChecker.hasScanPermission()) {
            BleLogger.w(BleLogger.Component.SCANNER, "Stop scan - permission denied")
            _scanState.value = BleScanState.Error(BleError.PERMISSION_DENIED, "Permission denied")
            return
        }

        scanJob?.cancel() // 타임아웃 취소
        if (adapter != null && adapter.isEnabled) {
            try {
                scanner.stopScan(scanCallback)
                BleLogger.success(BleLogger.Component.SCANNER, "Scan stopped (Found ${scannedDevices.size} devices)")
            } catch (e: Exception) {
                BleLogger.w(BleLogger.Component.SCANNER, "Error stopping scan", e)
            }
        }

        BleLogger.state(BleLogger.Component.SCANNER, _scanState.value, BleScanState.Stopped)
        _scanState.value = BleScanState.Stopped
    }

    // 메모리 누수 방지용 (필요시 호출)
    fun clear() {
        BleLogger.d(BleLogger.Component.SCANNER, "Clearing scanner resources")
        stopScan()
        scope.cancel()
    }
}