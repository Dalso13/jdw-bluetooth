package com.jdw.module.core.bluetooth.internal

import com.jdw.module.core.bluetooth.contract.BleLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 중첩 실행 방지용 큐
 */
internal class BleCommandQueue {
    // Mutex: 한 번에 하나만 실행되도록 잠금 장치
    private val mutex = Mutex()
    private var commandCount = 0

    suspend fun <T> enqueue(action: suspend () -> T): T {
        val currentCommand = ++commandCount
        BleLogger.v(BleLogger.Component.QUEUE, "Command #$currentCommand enqueued (waiting...)")

        mutex.withLock {
            BleLogger.v(BleLogger.Component.QUEUE, "Command #$currentCommand executing")

            return try {
                val result = action()
                BleLogger.v(BleLogger.Component.QUEUE, "Command #$currentCommand completed successfully")
                result
            } catch (e: Exception) {
                BleLogger.w(BleLogger.Component.QUEUE, "Command #$currentCommand failed: ${e.message}")
                throw e
            }
        }
    }
}