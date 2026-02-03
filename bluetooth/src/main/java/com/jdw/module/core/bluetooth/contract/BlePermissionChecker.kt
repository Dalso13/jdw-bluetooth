package com.jdw.module.core.bluetooth.contract

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BlePermissionChecker(
    context: Context
) {
    private val context = context.applicationContext

    // 스캔 권한
    fun hasScanPermission(): Boolean {
        // Android 12 (S) 이상
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        }
        // Android 11 이하 (위치 권한 필요)
        else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        BleLogger.v(BleLogger.Component.PERMISSION, "Scan permission check: $result")
        return result
    }

    // 연결 권한
    fun hasConnectPermission(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }

        BleLogger.v(BleLogger.Component.PERMISSION, "Connect permission check: $result")
        return result
    }

    // 헬퍼 함수
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}