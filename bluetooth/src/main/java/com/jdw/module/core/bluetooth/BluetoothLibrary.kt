package com.jdw.module.core.bluetooth

import android.content.Context
import com.jdw.module.core.bluetooth.contract.BleClient
import com.jdw.module.core.bluetooth.contract.BleConfig
import com.jdw.module.core.bluetooth.internal.DefaultBleClient

object JdwBluetooth {

    /**
     * BleClient 인스턴스를 생성하는 유일한 진입점
     * DI 라이브러리(Hilt, Koin)나 수동 주입 시 이 함수를 호출하면 됨.
     */
    fun createClient(
        context: Context,
        config: BleConfig,
    ): BleClient {
        return DefaultBleClient(context, config)
    }
}
