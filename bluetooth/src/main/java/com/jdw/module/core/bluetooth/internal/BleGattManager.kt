package com.jdw.module.core.bluetooth.internal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import com.jdw.module.core.bluetooth.contract.BleConfig
import com.jdw.module.core.bluetooth.contract.BleConnectionState
import com.jdw.module.core.bluetooth.contract.BleError
import com.jdw.module.core.bluetooth.contract.BleLogger
import com.jdw.module.core.bluetooth.contract.BlePermissionChecker
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
internal class BleGattManager(
    context: Context,
    private val config: BleConfig,
    private val permissionChecker: BlePermissionChecker,
    private val commandQueue: BleCommandQueue = BleCommandQueue()
) {

    init {
        BleLogger.isEnabled = config.isDebugMode
        BleLogger.i(BleLogger.Component.GATT, "GattManager initialized")
    }

    // 메모리 누수 방지를 위해 applicationContext 사용
    private val appContext = context.applicationContext

    // UI나 로직에서 구독할 연결 상태
    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    // 현재 연결된 GATT 객체 (나중에 Read/Write 할 때 필요함)
    var bluetoothGatt: BluetoothGatt? = null
        private set

    // 코루틴 스코프 (타임아웃 처리용)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var connectionTimeoutJob: Job? = null

    // Characteristic Write/Read 대기용 Continuation
    private var pendingWriteContinuation: CancellableContinuation<Result<Unit>>? = null
    private var pendingReadContinuation: CancellableContinuation<Result<ByteArray>>? = null

    // 타임아웃 job
    private var pendingWriteTimeoutJob: Job? = null
    private var pendingReadTimeoutJob: Job? = null

    // noti 구독 플로우
    private val _characteristicStream = MutableSharedFlow<Pair<String, ByteArray>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val characteristicStream: SharedFlow<Pair<String, ByteArray>> = _characteristicStream.asSharedFlow()

    /**
     * 연결 요청
     */
    fun connect(device: BluetoothDevice) {
        BleLogger.d(BleLogger.Component.GATT, "connect() called for device: ${device.name ?: "Unknown"} (${device.address})")

        // 1. 권한 체크
        if (!permissionChecker.hasConnectPermission()) {
            BleLogger.w(BleLogger.Component.GATT, "Connect permission denied")
            updateState(
                BleConnectionState.Error(
                    BleError.PERMISSION_DENIED,
                    "No Connect Permission"
                )
            )
            return
        }

        // 2. 이미 연결 시도 중이거나 연결된 경우 무시 (또는 재연결 정책)
        if (_connectionState.value is BleConnectionState.Connecting ||
            _connectionState.value is BleConnectionState.Ready
        ) {
            BleLogger.w(BleLogger.Component.GATT, "Already connecting or connected, ignoring")
            return
        }

        // 3. 기존 자원 정리 (매우 중요: 좀비 연결 방지)
        closeGatt()

        updateState(BleConnectionState.Connecting)

        // 4. 연결 타임아웃 타이머 시작 (안드로이드는 연결 타임아웃 콜백이 없음)
        startConnectionTimeout()

        // 5. GATT 연결 시도
        // autoConnect = false가 연결 속도가 훨씬 빠름 (true는 백그라운드 재연결용)
        // TRANSPORT_LE를 명시해야 듀얼모드 칩셋에서 버벅임이 덜함
        bluetoothGatt = device.connectGatt(
            appContext,
            config.shouldAutoConnect,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
        BleLogger.i(BleLogger.Component.GATT, "GATT connection initiated (autoConnect: ${config.shouldAutoConnect})")
    }

    /**
     * 연결 해제 요청
     */
    fun disconnect() {
        if (bluetoothGatt == null) {
            BleLogger.w(BleLogger.Component.GATT, "disconnect() called but GATT is null")
            return
        }

        BleLogger.d(BleLogger.Component.GATT, "disconnect() called")
        updateState(BleConnectionState.Disconnecting)

        try {
            bluetoothGatt?.disconnect()
            BleLogger.i(BleLogger.Component.GATT, "Disconnection requested")
            // 여기서 바로 close()하지 않음! onConnectionStateChange에서 Disconnected 콜백 오면 그때 close함.
            // 그래야 확실하게 끊김.
        } catch (_: SecurityException) {
            BleLogger.failure(BleLogger.Component.GATT, "Disconnect permission denied")
            updateState(
                BleConnectionState.Error(
                    BleError.PERMISSION_DENIED,
                    "Disconnect permission denied"
                )
            )
        }
    }

    /**
     * 자원 완전 정리 (앱 종료 시 등)
     */
    fun close() {
        BleLogger.d(BleLogger.Component.GATT, "close() called - cleaning up all resources")
        connectionTimeoutJob?.cancel()
        pendingWriteTimeoutJob?.cancel()
        pendingReadTimeoutJob?.cancel()
        closeGatt()
        updateState(BleConnectionState.Disconnected)
    }

    // =====================================================================================
    // Write / Read 기능 (CommandQueue 활용)
    // =====================================================================================

    /**
     * Characteristic에 데이터 쓰기 (순차 실행 보장)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun writeCharacteristic(
        characteristicUuid: String,
        data: ByteArray,
        serviceUuid: String? = null,
        writeType: Int? = null
    ): Result<Unit> = commandQueue.enqueue {
        BleLogger.d(BleLogger.Component.GATT, "writeCharacteristic() called for UUID: $characteristicUuid")
        BleLogger.data(BleLogger.Component.GATT, "Write data", data)

        suspendCancellableCoroutine { continuation ->
            try {
                val svcUuid = serviceUuid ?: config.serviceUuid
                val wt = writeType ?: BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                val service = bluetoothGatt?.getService(UUID.fromString(svcUuid))
                val char = service?.getCharacteristic(UUID.fromString(characteristicUuid))

                if (char == null) {
                    BleLogger.failure(BleLogger.Component.GATT, "Characteristic not found: $characteristicUuid")
                    continuation.resume(Result.failure(IllegalStateException("Characteristic not found: $characteristicUuid")))
                    return@suspendCancellableCoroutine
                }

                // Android 13+ 최신 API 사용
                val success =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val result = bluetoothGatt?.writeCharacteristic(char, data, wt)
                        BleLogger.v(BleLogger.Component.GATT, "Write API call result (Android 13+): $result")
                        result == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        char.value = data
                        @Suppress("DEPRECATION")
                        char.writeType = wt
                        @Suppress("DEPRECATION")
                        val result = bluetoothGatt?.writeCharacteristic(char) ?: false
                        BleLogger.v(BleLogger.Component.GATT, "Write API call result (Legacy): $result")
                        result
                    }

                if (success) {
                    BleLogger.v(BleLogger.Component.GATT, "Write request queued, waiting for callback...")
                    pendingWriteContinuation = continuation

                    // 타임아웃 설정
                    pendingWriteTimeoutJob = scope.launch {
                        delay(5000L)
                        if (pendingWriteContinuation === continuation) {
                            BleLogger.w(BleLogger.Component.GATT, "Write timeout!")
                            pendingWriteContinuation?.resumeWith(Result.failure(TimeoutException("Write Timeout")))
                            pendingWriteContinuation = null
                        }
                    }

                    continuation.invokeOnCancellation {
                        BleLogger.v(BleLogger.Component.GATT, "Write cancelled")
                        pendingWriteContinuation = null
                    }
                } else {
                    BleLogger.failure(BleLogger.Component.GATT, "writeCharacteristic() returned false")
                    continuation.resume(Result.failure(Exception("writeCharacteristic() returned false")))
                }
            } catch (e: Exception) {
                BleLogger.failure(BleLogger.Component.GATT, "Write exception", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Characteristic 읽기 (순차 실행 보장)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun readCharacteristic(characteristicUuid: String): Result<ByteArray> =
        commandQueue.enqueue {
            BleLogger.d(BleLogger.Component.GATT, "readCharacteristic() called for UUID: $characteristicUuid")

            suspendCancellableCoroutine { continuation ->
                try {
                    val service = bluetoothGatt?.getService(UUID.fromString(config.serviceUuid))
                    val char = service?.getCharacteristic(UUID.fromString(characteristicUuid))

                    if (char == null) {
                        BleLogger.failure(BleLogger.Component.GATT, "Characteristic not found: $characteristicUuid")
                        continuation.resume(Result.failure(IllegalStateException("Characteristic not found: $characteristicUuid")))
                        return@suspendCancellableCoroutine
                    }

                    val success = bluetoothGatt?.readCharacteristic(char) ?: false

                    if (success) {
                        BleLogger.v(BleLogger.Component.GATT, "Read request queued, waiting for callback...")
                        pendingReadContinuation = continuation

                        // 타임아웃 설정
                        pendingReadTimeoutJob = scope.launch {
                            delay(5000L)
                            if (pendingReadContinuation === continuation) {
                                BleLogger.w(BleLogger.Component.GATT, "Read timeout!")
                                pendingReadContinuation?.resumeWith(Result.failure(TimeoutException("Read timeout")))
                                pendingReadContinuation = null
                            }
                        }

                        continuation.invokeOnCancellation {
                            BleLogger.v(BleLogger.Component.GATT, "Read cancelled")
                            pendingReadTimeoutJob?.cancel()
                            pendingReadContinuation = null
                        }
                    } else {
                        BleLogger.failure(BleLogger.Component.GATT, "readCharacteristic() returned false")
                        continuation.resume(Result.failure(Exception("readCharacteristic() returned false")))
                    }
                } catch (e: Exception) {
                    BleLogger.failure(BleLogger.Component.GATT, "Read exception", e)
                    continuation.resume(Result.failure(e))
                }
            }
        }

    // =====================================================================================
    // GATT Callback (핵심 로직)
    // =====================================================================================
    private val gattCallback = object : BluetoothGattCallback() {

        // 연결 상태 변화 감지
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            BleLogger.d(BleLogger.Component.GATT, "onConnectionStateChange: status=$status, newState=$newState")

            connectionTimeoutJob?.cancel() // 응답 왔으니 타임아웃 취소

            // 에러 발생 (GATT_SUCCESS(0)가 아닌 모든 경우)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val errorType =
                    if (status == 133) BleError.GATT_ERROR else BleError.DISCONNECTED_BY_DEVICE
                BleLogger.failure(BleLogger.Component.GATT, "Connection failed with status: $status (${if (status == 133) "GATT_ERROR_133" else "DISCONNECTED"})")
                updateState(BleConnectionState.Error(errorType, "Gatt Status Error: $status"))
                closeGatt() // 에러 나면 무조건 닫아야 함
                return
            }

            // 연결 성공
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                BleLogger.success(BleLogger.Component.GATT, "Device connected")
                // 아직 Ready 아님! 서비스 발견을 해야 함
                updateState(BleConnectionState.Discovering)

                // 서비스 발견 시작 (이게 끝나야 진짜 통신 가능)
                // 약간의 딜레이를 주는 것이 안정성 높임 (삼성/샤오미 이슈)
                scope.launch {
                    delay(config.discoveryDelayMillis)
                    BleLogger.d(BleLogger.Component.GATT, "Starting service discovery (after ${config.discoveryDelayMillis}ms delay)")
                    try {
                        val success = gatt.discoverServices()
                        if (!success) {
                            BleLogger.failure(BleLogger.Component.GATT, "Service discovery start failed")
                            updateState(
                                BleConnectionState.Error(
                                    BleError.GATT_ERROR,
                                    "Service Discovery Start Failed"
                                )
                            )
                            disconnect()
                        }
                    } catch (_: SecurityException) {
                        BleLogger.failure(BleLogger.Component.GATT, "Discovery permission denied")
                        updateState(
                            BleConnectionState.Error(
                                BleError.PERMISSION_DENIED,
                                "Discovery Permission Denied"
                            )
                        )
                    }
                }
            }
            // 연결 해제됨
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                BleLogger.i(BleLogger.Component.GATT, "Device disconnected")
                updateState(BleConnectionState.Disconnected)
                closeGatt() // 완전히 닫기
            }
        }

        // 서비스 발견 결과 (여기까지 와야 진짜 성공)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            BleLogger.d(BleLogger.Component.GATT, "onServicesDiscovered: status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 발견된 서비스 로깅
                val services = gatt.services
                BleLogger.i(BleLogger.Component.GATT, "Discovered ${services.size} services")
                services.forEach { service ->
                    BleLogger.v(BleLogger.Component.GATT, "  Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        BleLogger.v(BleLogger.Component.GATT, "    Char: ${char.uuid}")
                    }
                }

                // Notification 사용시 자동 활성화
                if (config.enableNotificationOnConnect){
                    enableNotification()
                }

                // 통신 준비 완료
                BleLogger.success(BleLogger.Component.GATT, "Service discovery completed, device ready!")
                updateState(BleConnectionState.Ready)
            } else {
                BleLogger.failure(BleLogger.Component.GATT, "Service discovery failed with status: $status")
                updateState(
                    BleConnectionState.Error(
                        BleError.GATT_ERROR,
                        "Service Discovery Failed: $status"
                    )
                )
                disconnect()
            }
        }

        // Write 결과 콜백
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            BleLogger.d(BleLogger.Component.GATT, "onCharacteristicWrite: UUID=${characteristic.uuid}, status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleLogger.success(BleLogger.Component.GATT, "Write completed successfully")
                pendingWriteContinuation?.resume(Result.success(Unit))
            } else {
                BleLogger.failure(BleLogger.Component.GATT, "Write failed with status: $status")
                pendingWriteContinuation?.resume(Result.failure(Exception("Write failed with status: $status")))
            }
            pendingWriteTimeoutJob?.cancel()
            pendingWriteContinuation = null
        }

        // Read 결과 콜백 (Android 12 이하)
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            BleLogger.d(BleLogger.Component.GATT, "onCharacteristicRead (Android 12-): UUID=${characteristic.uuid}, status=$status")
            pendingReadTimeoutJob?.cancel()

            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: ByteArray(0)
                BleLogger.data(BleLogger.Component.GATT, "Read data", data)
                pendingReadContinuation?.resume(Result.success(data))
            } else {
                BleLogger.failure(BleLogger.Component.GATT, "Read failed with status: $status")
                pendingReadContinuation?.resume(Result.failure(Exception("Read failed with status: $status")))
            }
            pendingReadContinuation = null
        }

        // Read 결과 콜백 (Android 13+)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            BleLogger.d(BleLogger.Component.GATT, "onCharacteristicRead (Android 13+): UUID=${characteristic.uuid}, status=$status")
            pendingReadTimeoutJob?.cancel()

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleLogger.data(BleLogger.Component.GATT, "Read data", value)
                pendingReadContinuation?.resume(Result.success(value))
            } else {
                BleLogger.failure(BleLogger.Component.GATT, "Read failed with status: $status")
                pendingReadContinuation?.resume(Result.failure(Exception("Read failed with status: $status")))
            }
            pendingReadContinuation = null
        }

        // Characteristic 변경 알림 (Android 12 이하)
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: return
            BleLogger.data(BleLogger.Component.GATT, "Notification (Android 12-)", data)

            scope.launch {
                _characteristicStream.emit(characteristic.uuid.toString() to data)
            }
        }

        // Characteristic 변경 알림 (Android 13+)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            BleLogger.data(BleLogger.Component.GATT, "Notification (Android 13+)", value)

            scope.launch {
                _characteristicStream.emit(characteristic.uuid.toString() to value)
            }
        }
    }

    // =====================================================================================
    // 내부 유틸 함수
    // =====================================================================================

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(config.connectionTimeoutMillis)

            // 아직도 연결 중이라면?
            if (_connectionState.value is BleConnectionState.Connecting ||
                _connectionState.value is BleConnectionState.Discovering
            ) {
                BleLogger.w(BleLogger.Component.GATT, "Connection timeout after ${config.connectionTimeoutMillis}ms")
                updateState(BleConnectionState.Error(BleError.TIMEOUT, "Connection Timeout"))
                closeGatt() // 강제 종료
            }
        }
    }

    private fun closeGatt() {
        BleLogger.v(BleLogger.Component.GATT, "closeGatt() - cleaning pending operations")

        // 대기 중인 작업들 모두 실패 처리 (CommandQueue 멈춤 방지)
        pendingWriteTimeoutJob?.cancel()
        pendingReadTimeoutJob?.cancel()

        pendingWriteContinuation?.resume(Result.failure(Exception("Connection closed")))
        pendingReadContinuation?.resume(Result.failure(Exception("Connection closed")))

        pendingWriteContinuation = null
        pendingReadContinuation = null

        try {
            bluetoothGatt?.close()
            BleLogger.v(BleLogger.Component.GATT, "GATT closed")
        } catch (_: Exception) { /* 무시 */
        }
        bluetoothGatt = null
    }

    /**
     * Notification 활성화 (Config의 notifyCharUuid 사용)
     */
    private fun enableNotification() {
        BleLogger.d(BleLogger.Component.GATT, "enableNotification() called")

        if (config.notifyCharUuid == null) {
            BleLogger.w(BleLogger.Component.GATT, "Notification not enabled - notifyCharUuid is null")
            return
        }

        try {
            val service = bluetoothGatt?.getService(UUID.fromString(config.serviceUuid))
            val char = service?.getCharacteristic(UUID.fromString(config.notifyCharUuid))

            if (char == null) {
                BleLogger.w(BleLogger.Component.GATT, "Notification characteristic not found: ${config.notifyCharUuid}")
                return
            }

            // 1. Local notification 활성화
            val registered = bluetoothGatt?.setCharacteristicNotification(char, true) ?: false
            if (!registered) {
                BleLogger.w(BleLogger.Component.GATT, "setCharacteristicNotification failed")
                return
            }

            BleLogger.v(BleLogger.Component.GATT, "Local notification enabled for ${char.uuid}")

            // 2. Remote (기기측) Descriptor 설정 (필수!)
            val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeDescriptor(
                        descriptor,
                        android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    bluetoothGatt?.writeDescriptor(descriptor)
                }
                BleLogger.success(BleLogger.Component.GATT, "Notification descriptor written")
            } else {
                BleLogger.w(BleLogger.Component.GATT, "Notification descriptor not found")
            }
        } catch (e: Exception) {
            BleLogger.failure(BleLogger.Component.GATT, "Enable notification failed", e)
        }
    }

    private fun updateState(newState: BleConnectionState) {
        BleLogger.state(BleLogger.Component.GATT, _connectionState.value, newState)
        scope.launch {
            _connectionState.value = newState
        }
    }
}
