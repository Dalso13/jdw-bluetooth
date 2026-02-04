package com.jdw.bluetooth.test

import android.bluetooth.le.ScanSettings
import com.jdw.module.core.bluetooth.contract.BleConfig

data class MyBleConfig(
    override val serviceUuid: String = "0000180d-0000-1000-8000-00805f9b34fb",
    override val enableNotificationOnConnect: Boolean = true,
    override val notifyCharUuid: String = "00002a37-0000-1000-8000-00805f9b34fb",
    override val scanTimeoutMillis: Long = 10_000L,
    override val isDebugMode: Boolean = BuildConfig.DEBUG,
    override val shouldAutoConnect: Boolean = false,
    override val scanMode: Int = ScanSettings.SCAN_MODE_BALANCED
) : BleConfig
