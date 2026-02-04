# BLE 라이브러리 사용 가이드

## 0. 사용법
```kotlin
// setting.gradle.kts
	dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url = uri("https://jitpack.io") }
		}
	}
```
```kotlin
// build.gradle (app)
    dependencies {
        implementation 'com.github.Dalso13:jdw-bluetooth:v0.0.1'
    }
```


## 📱 1. 기본 설정

### Config 정의
```kotlin
data class MyBleConfig(
    override val serviceUuid: String = "0000180d-0000-1000-8000-00805f9b34fb",
    override val enableNotificationOnConnect: Boolean = true,
    override val notifyCharUuid: String = "00002a37-0000-1000-8000-00805f9b34fb",
    override val scanTimeoutMillis: Long = 10_000L,
    override val isDebugMode: Boolean = BuildConfig.DEBUG,
    override val shouldAutoConnect: Boolean = false,
    override val scanMode: Int = ScanSettings.SCAN_MODE_BALANCED
) : BleConfig
```

### Client 생성
```kotlin
val bleClient = BluetoothLibrary.createClient(
    context = applicationContext,
    config = MyBleConfig()
)
```

---

## 🔍 2. 스캔 (Scan)

### ViewModel에서 스캔
```kotlin
class BleViewModel(private val bleClient: BleClient) : ViewModel() {
    
    val scanState = bleClient.scanState
        .stateIn(viewModelScope, SharingStarted.Lazily, BleScanState.Idle)
    
    fun startScan() {
        bleClient.startScan()
    }
    
    fun stopScan() {
        bleClient.stopScan()
    }
}
```

### Compose UI에서 스캔 결과 표시
```kotlin
@Composable
fun ScanScreen(viewModel: BleViewModel) {
    val scanState by viewModel.scanState.collectAsState()
    
    when (val state = scanState) {
        BleScanState.Idle -> Text("스캔 대기 중")
        BleScanState.Scanning -> CircularProgressIndicator()
        is BleScanState.Scanned -> {
            LazyColumn {
                items(state.results) { scanResult ->
                    DeviceItem(
                        name = scanResult.device.name ?: "Unknown",
                        address = scanResult.device.address,
                        rssi = scanResult.rssi,
                        onClick = { viewModel.connect(scanResult.device) }
                    )
                }
            }
        }
        BleScanState.Stopped -> Text("스캔 완료")
        is BleScanState.Error -> Text("에러: ${state.message}")
    }
    
    Button(onClick = { viewModel.startScan() }) {
        Text("스캔 시작")
    }
}
```

---

## 🔗 3. 연결 (Connect)

### ViewModel에서 연결
```kotlin
class BleViewModel(private val bleClient: BleClient) : ViewModel() {
    
    val connectionState = bleClient.connectionState
        .stateIn(viewModelScope, SharingStarted.Lazily, BleConnectionState.Disconnected)
    
    fun connect(device: BluetoothDevice) {
        bleClient.connect(device)
    }
    
    fun disconnect() {
        bleClient.disconnect()
    }
}
```

### Compose UI에서 연결 상태 표시
```kotlin
@Composable
fun ConnectionScreen(viewModel: BleViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    
    when (val state = connectionState) {
        BleConnectionState.Disconnected -> Text("연결 끊김")
        BleConnectionState.Connecting -> CircularProgressIndicator()
        BleConnectionState.Discovering -> Text("서비스 검색 중...")
        BleConnectionState.Ready -> Text("✅ 연결됨! 통신 가능")
        BleConnectionState.Disconnecting -> Text("연결 해제 중...")
        is BleConnectionState.Error -> Text("에러: ${state.msg}")
    }
}
```

---

## 📤 4. Write (데이터 쓰기)

### 기본 Write
```kotlin
class BleViewModel(private val bleClient: BleClient) : ViewModel() {
    
    fun sendCommand(command: ByteArray) {
        viewModelScope.launch {
            // Ready 상태인지 확인
            if (connectionState.value !is BleConnectionState.Ready) {
                Log.e("BLE", "Not connected!")
                return@launch
            }
            
            // Write 실행
            val result = bleClient.writeCharacteristic(
                characteristicUuid = "00002a39-0000-1000-8000-00805f9b34fb",
                data = command,
                serviceUuid = null,  // null이면 Config의 기본값 사용
                writeType = null     // null이면 WRITE_TYPE_DEFAULT
            )
            
            result.onSuccess {
                Log.d("BLE", "✅ Write 성공!")
            }.onFailure { error ->
                Log.e("BLE", "❌ Write 실패: ${error.message}")
            }
        }
    }
}
```

### 실전 예제: LED 제어
```kotlin
fun turnOnLed() {
    viewModelScope.launch {
        val command = byteArrayOf(0x01, 0xFF.toByte())  // LED ON 명령
        
        bleClient.writeCharacteristic(
            characteristicUuid = "LED_CONTROL_UUID",
            data = command,
            serviceUuid = null,
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE  // 응답 불필요
        ).onSuccess {
            _ledState.value = true
        }.onFailure {
            showError("LED 제어 실패")
        }
    }
}
```

### Write 옵션 설명
```kotlin
writeCharacteristic(
    characteristicUuid = "...",
    data = byteArrayOf(0x01, 0x02),
    
    // serviceUuid: 다른 서비스의 Characteristic에 쓸 때
    serviceUuid = "custom-service-uuid",  // null이면 Config의 기본값
    
    // writeType: 쓰기 방식 선택
    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT       // 응답 기다림 (느리지만 안전)
    writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE   // 응답 안 기다림 (빠름)
)
```

---

## 📥 5. Read (데이터 읽기)

### 기본 Read
```kotlin
class BleViewModel(private val bleClient: BleClient) : ViewModel() {
    
    fun readBatteryLevel() {
        viewModelScope.launch {
            if (connectionState.value !is BleConnectionState.Ready) {
                return@launch
            }
            
            val result = bleClient.readCharacteristic(
                characteristicUuid = "00002a19-0000-1000-8000-00805f9b34fb"  // Battery Level
            )
            
            result.onSuccess { data ->
                val batteryLevel = data[0].toInt() and 0xFF  // ByteArray → Int
                Log.d("BLE", "🔋 배터리: $batteryLevel%")
                _batteryLevel.value = batteryLevel
            }.onFailure { error ->
                Log.e("BLE", "❌ Read 실패: ${error.message}")
            }
        }
    }
}
```

### 실전 예제: 센서 값 읽기
```kotlin
private val _temperature = MutableStateFlow<Float?>(null)
val temperature: StateFlow<Float?> = _temperature.asStateFlow()

fun readTemperature() {
    viewModelScope.launch {
        bleClient.readCharacteristic("TEMPERATURE_UUID")
            .onSuccess { data ->
                // ByteArray를 Float로 변환 (Little Endian 가정)
                val temp = ByteBuffer.wrap(data)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getFloat()
                    
                _temperature.value = temp
                Log.d("BLE", "🌡️ 온도: $temp°C")
            }
            .onFailure {
                _temperature.value = null
            }
    }
}
```

---

## 🔔 6. Notification (실시간 데이터 수신)

### 기본 Notification 구독
```kotlin
class BleViewModel(private val bleClient: BleClient) : ViewModel() {
    
    init {
        // ViewModel 생성 시 자동으로 구독 시작
        viewModelScope.launch {
            bleClient.notifyFlow.collect { data ->
                Log.d("BLE", "📨 Notification 수신: ${data.toHexString()}")
                handleNotificationData(data)
            }
        }
    }
    
    private fun handleNotificationData(data: ByteArray) {
        // 프로토콜에 맞게 파싱
        when (data[0]) {
            0x01.toByte() -> handleHeartRate(data)
            0x02.toByte() -> handleTemperature(data)
            else -> Log.w("BLE", "알 수 없는 데이터")
        }
    }
}
```

### 실전 예제: 심박수 모니터링
```kotlin
private val _heartRate = MutableStateFlow<Int>(0)
val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

init {
    // Notification 자동 구독
    viewModelScope.launch {
        bleClient.notifyFlow.collect { data ->
            if (data.isNotEmpty()) {
                val bpm = data[1].toInt() and 0xFF  // 심박수는 두 번째 바이트
                _heartRate.value = bpm
                Log.d("BLE", "💓 심박수: $bpm BPM")
            }
        }
    }
}

@Composable
fun HeartRateDisplay(viewModel: BleViewModel) {
    val heartRate by viewModel.heartRate.collectAsState()
    
    Text(
        text = "💓 $heartRate BPM",
        fontSize = 48.sp,
        color = if (heartRate > 100) Color.Red else Color.Green
    )
}
```

---

## 🔄 7. 전체 플로우 예제

### ViewModel (전체)
```kotlin
class BleViewModel(private val bleClient: BleClient) : ViewModel() {
    
    val scanState = bleClient.scanState.stateIn(viewModelScope, SharingStarted.Lazily, BleScanState.Idle)
    val connectionState = bleClient.connectionState.stateIn(viewModelScope, SharingStarted.Lazily, BleConnectionState.Disconnected)
    
    private val _receivedData = MutableStateFlow<String>("")
    val receivedData: StateFlow<String> = _receivedData.asStateFlow()
    
    init {
        // Notification 구독
        viewModelScope.launch {
            bleClient.notifyFlow.collect { (uuid, data) ->
                _receivedData.value = "수신 from $uuid: ${data.toHexString()}"
            }
        }
    }
    
    // 1. 스캔
    fun startScan() = bleClient.startScan()
    
    // 2. 연결
    fun connect(device: BluetoothDevice) = bleClient.connect(device)
    
    // 3. 데이터 쓰기
    fun sendData(text: String) {
        viewModelScope.launch {
            val data = text.toByteArray()
            
            bleClient.writeCharacteristic(
                characteristicUuid = "WRITE_UUID",
                data = data,
                serviceUuid = null,
                writeType = null
            ).onSuccess {
                Log.d("BLE", "✅ 전송 성공")
            }.onFailure { error ->
                Log.e("BLE", "❌ 전송 실패: $error")
            }
        }
    }
    
    // 4. 데이터 읽기
    fun readSensorValue() {
        viewModelScope.launch {
            bleClient.readCharacteristic("SENSOR_UUID")
                .onSuccess { data ->
                    _receivedData.value = "읽기: ${data.toHexString()}"
                }
                .onFailure { error ->
                    Log.e("BLE", "읽기 실패: $error")
                }
        }
    }
    
    // 5. 연결 해제
    fun disconnect() = bleClient.disconnect()
    
    // 6. 정리
    override fun onCleared() {
        super.onCleared()
        bleClient.close()
    }
}

// ByteArray를 Hex String으로 변환하는 유틸
private fun ByteArray.toHexString(): String {
    return joinToString(" ") { "%02X".format(it) }
}
```

---

## 🎨 8. Compose UI 전체 예제

```kotlin
@Composable
fun BleScreen(viewModel: BleViewModel) {
    val scanState by viewModel.scanState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val receivedData by viewModel.receivedData.collectAsState()
    
    Column(modifier = Modifier.padding(16.dp)) {
        // 연결 상태
        Text("연결: ${connectionState}")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 스캔 버튼
        Button(onClick = { viewModel.startScan() }) {
            Text("스캔 시작")
        }
        
        // 스캔 결과
        when (val state = scanState) {
            is BleScanState.Scanned -> {
                LazyColumn {
                    items(state.results) { scanResult ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.connect(scanResult.device) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("이름: ${scanResult.device.name ?: "Unknown"}")
                                Text("주소: ${scanResult.device.address}")
                                Text("신호: ${scanResult.rssi} dBm")
                            }
                        }
                    }
                }
            }
            else -> {}
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 연결 후 사용 가능한 버튼들
        if (connectionState is BleConnectionState.Ready) {
            // Write 버튼
            Button(onClick = { 
                viewModel.sendData("Hello BLE!") 
            }) {
                Text("데이터 전송")
            }
            
            // Read 버튼
            Button(onClick = { 
                viewModel.readSensorValue() 
            }) {
                Text("센서 값 읽기")
            }
            
            // 수신 데이터 표시
            Text("수신 데이터: $receivedData")
            
            // 연결 해제
            Button(onClick = { viewModel.disconnect() }) {
                Text("연결 해제")
            }
        }
    }
}
```

---

## 💡 9. 자주 하는 실수 및 팁

### ❌ 실수 1: Ready 상태 확인 안 함
```kotlin
// ❌ 잘못된 코드
fun sendData() {
    viewModelScope.launch {
        bleClient.writeCharacteristic(...)  // Disconnected 상태에서 호출하면 실패
    }
}

// ✅ 올바른 코드
fun sendData() {
    viewModelScope.launch {
        if (connectionState.value !is BleConnectionState.Ready) {
            Log.e("BLE", "Not ready!")
            return@launch
        }
        bleClient.writeCharacteristic(...)
    }
}
```

### ❌ 실수 2: Result 처리 안 함
```kotlin
// ❌ 결과 무시
bleClient.writeCharacteristic(uuid, data, null, null)

// ✅ 결과 처리
bleClient.writeCharacteristic(uuid, data, null, null)
    .onSuccess { /* 성공 처리 */ }
    .onFailure { error -> /* 에러 처리 */ }
```

### ❌ 실수 3: Notification 구독 시점
```kotlin
// ❌ Ready 되기 전에 구독하면 데이터 못 받을 수 있음
init {
    viewModelScope.launch {
        bleClient.notifyFlow.collect { data -> ... }
    }
}

// ✅ 연결 상태 확인 후 구독 (또는 항상 구독해두기)
init {
    viewModelScope.launch {
        connectionState.collect { state ->
            if (state is BleConnectionState.Ready) {
                // Notification 활성화는 자동으로 됨
                // 그냥 notifyFlow 구독하면 됨
            }
        }
    }
    
    // 또는 그냥 항상 구독 (권장)
    viewModelScope.launch {
        bleClient.notifyFlow.collect { data ->
            // Ready 상태일 때만 데이터가 들어옴
            handleData(data)
        }
    }
}
```

---

## 🚀 10. 고급 패턴: 요청-응답 패턴

BLE에서는 Write 후 Notification으로 응답을 받는 경우가 많습니다.

```kotlin
class BleViewModel(private val bleClient: BleClient) : ViewModel() {
    
    suspend fun requestDataWithResponse(command: ByteArray): Result<ByteArray> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            
            var job: Job? = null
            
            // 1. Notification 대기
            job = viewModelScope.launch {
                bleClient.notifyFlow
                    .first()  // 첫 번째 데이터만 받음
                    .let { response ->
                        continuation.resume(Result.success(response))
                    }
            }
            
            // 2. Write 실행
            viewModelScope.launch {
                bleClient.writeCharacteristic("UUID", command, null, null)
                    .onFailure { error ->
                        job?.cancel()
                        continuation.resume(Result.failure(error))
                    }
            }
            
            // 3. 타임아웃 (5초)
            viewModelScope.launch {
                delay(5000L)
                job?.cancel()
                if (continuation.isActive) {
                    continuation.resume(Result.failure(TimeoutException("No response")))
                }
            }
        }
    }
}
```

---

## 📝 11. ByteArray 변환 유틸리티

```kotlin
// String → ByteArray
val data = "Hello".toByteArray()

// Int → ByteArray (4 bytes, Little Endian)
fun Int.toByteArray(): ByteArray {
    return ByteBuffer.allocate(4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(this)
        .array()
}

// ByteArray → Int
fun ByteArray.toInt(): Int {
    return ByteBuffer.wrap(this)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int
}

// ByteArray → Hex String (디버깅용)
fun ByteArray.toHexString(): String {
    return joinToString(" ") { "%02X".format(it) }
}

// Hex String → ByteArray
fun String.hexToByteArray(): ByteArray {
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
```

---

## 🎯 12. 정리

### Write vs Read vs Notification

| 기능 | 방향 | 용도 | 예시 |
|------|------|------|------|
| **Write** | 앱 → 기기 | 명령 전송 | LED 제어, 설정 변경 |
| **Read** | 앱 ← 기기 (1회) | 현재 값 조회 | 배터리 레벨, 버전 정보 |
| **Notification** | 앱 ← 기기 (지속) | 실시간 스트림 | 심박수, 센서 데이터 |

### 사용 패턴

```kotlin
// Write: 명령 보내기
bleClient.writeCharacteristic(uuid, command, null, null)

// Read: 값 읽어오기 (1회성)
val result = bleClient.readCharacteristic(uuid)

// Notification: 계속 듣기
bleClient.notifyFlow.collect { data -> 
    // 기기에서 데이터 보낼 때마다 자동으로 호출됨
}
```

이제 완벽하게 사용할 수 있습니다! 🚀
