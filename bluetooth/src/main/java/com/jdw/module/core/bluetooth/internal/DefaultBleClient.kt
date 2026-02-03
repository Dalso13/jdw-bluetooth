package com.jdw.module.core.bluetooth.internal

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.jdw.module.core.bluetooth.contract.BleClient
import com.jdw.module.core.bluetooth.contract.BleConfig
import com.jdw.module.core.bluetooth.contract.BleLogger
import com.jdw.module.core.bluetooth.contract.BlePermissionChecker
import kotlinx.coroutines.flow.SharedFlow

internal class DefaultBleClient(
    context: Context,
    val config: BleConfig,
) : BleClient {

    init {
        BleLogger.isEnabled = config.isDebugMode
        BleLogger.i(BleLogger.Component.CLIENT, "DefaultBleClient created")
    }

    private val appContext = context.applicationContext

    // 권한체크 용 클래스
    private val permissionChecker = BlePermissionChecker(appContext)

    // 블루투스 매니저
    private val bluetoothManager by lazy {
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val scanner by lazy {
        BleScanner(config, permissionChecker, bluetoothManager)
    }

    private val gattManager by lazy {
        BleGattManager(appContext, config, permissionChecker)
    }

    override val scanState by lazy {
        scanner.scanState
    }

    override val connectionState by lazy {
        gattManager.connectionState
    }

    override val notifyFlow: SharedFlow<Pair<String, ByteArray>> by lazy {
        gattManager.characteristicStream
    }

    override fun startScan() {
        BleLogger.d(BleLogger.Component.CLIENT, "startScan() delegated to scanner")
        scanner.startScan()
    }

    override fun stopScan() {
        BleLogger.d(BleLogger.Component.CLIENT, "stopScan() delegated to scanner")
        scanner.stopScan()
    }

    override fun connect(device: BluetoothDevice) {
        BleLogger.d(BleLogger.Component.CLIENT, "connect() delegated to gattManager")
        gattManager.connect(device)
    }

    override suspend fun writeCharacteristic(
        characteristicUuid: String,
        data: ByteArray,
        serviceUuid: String?,
        writeType: Int?
    ): Result<Unit> {
        BleLogger.d(BleLogger.Component.CLIENT, "writeCharacteristic() delegated to gattManager")
        return gattManager.writeCharacteristic(
            characteristicUuid = characteristicUuid,
            data = data,
            serviceUuid = serviceUuid,
            writeType = writeType
        )
    }

    override suspend fun readCharacteristic(characteristicUuid: String): Result<ByteArray> {
        BleLogger.d(BleLogger.Component.CLIENT, "readCharacteristic() delegated to gattManager")
        return gattManager.readCharacteristic(characteristicUuid)
    }

    override fun disconnect() {
        BleLogger.d(BleLogger.Component.CLIENT, "disconnect() delegated to gattManager")
        gattManager.disconnect()
    }

    override fun close() {
        BleLogger.i(BleLogger.Component.CLIENT, "close() - cleaning up scanner and gattManager")
        scanner.clear()
        gattManager.close()
        BleLogger.i(BleLogger.Component.CLIENT, "DefaultBleClient closed")
    }
}