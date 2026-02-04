package com.jdw.bluetooth.test

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jdw.bluetooth.test.ui.theme.Ble_testTheme
import com.jdw.module.core.bluetooth.BluetoothLibrary
import com.jdw.module.core.bluetooth.contract.BleConnectionState
import com.jdw.module.core.bluetooth.contract.BleScanState

class MainActivity : ComponentActivity() {

    private lateinit var bleViewModel: BleViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BLE Client 생성
        val bleClient = BluetoothLibrary.createClient(
            context = applicationContext,
            config = MyBleConfig()
        )

        bleViewModel = BleViewModel(bleClient)

        enableEdgeToEdge()
        setContent {
            Ble_testTheme {
                BleTestScreen(viewModel = bleViewModel, activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleTestScreen(viewModel: BleViewModel, activity: ComponentActivity) {
    val scanState by viewModel.scanState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val receivedData by viewModel.receivedData.collectAsState()
    val heartRate by viewModel.heartRate.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()

    var hasPermissions by remember { mutableStateOf(false) }

    // 권한 요청
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            Toast.makeText(activity, "권한 승인됨", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "권한 필요", Toast.LENGTH_SHORT).show()
        }
    }

    // 권한 체크
    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            permissionLauncher.launch(permissions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Test App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // 권한 상태
            if (!hasPermissions) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = "⚠️ BLE 권한이 필요합니다",
                        modifier = Modifier.padding(16.dp),
                        color = Color.Red
                    )
                }
            }

            // 연결 상태
            StatusCard(
                title = "연결 상태",
                status = when (connectionState) {
                    BleConnectionState.Disconnected -> "🔴 연결 끊김"
                    BleConnectionState.Connecting -> "🟡 연결 중..."
                    BleConnectionState.Discovering -> "🟡 서비스 검색 중..."
                    BleConnectionState.Ready -> "🟢 연결됨 (Ready)"
                    BleConnectionState.Disconnecting -> "🟡 연결 해제 중..."
                    is BleConnectionState.Error -> "❌ 에러: ${(connectionState as BleConnectionState.Error).msg}"
                }
            )

            // 심박수 표시
            if (heartRate > 0) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = if (heartRate > 100) Color.Red.copy(alpha = 0.1f) else Color.Green.copy(alpha = 0.1f)
                )) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "💓 $heartRate BPM",
                            fontSize = 32.sp,
                            color = if (heartRate > 100) Color.Red else Color.Green
                        )
                    }
                }
            }

            // 배터리 표시
            batteryLevel?.let { battery ->
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🔋 배터리", style = MaterialTheme.typography.titleMedium)
                        Text("$battery%", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // 수신 데이터
            if (receivedData.isNotEmpty()) {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📥 수신 데이터:", style = MaterialTheme.typography.titleSmall)
                        Text(receivedData, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Divider()

            // 스캔 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.startScan() },
                    modifier = Modifier.weight(1f),
                    enabled = hasPermissions && scanState !is BleScanState.Scanning
                ) {
                    Text("🔍 스캔 시작")
                }

                Button(
                    onClick = { viewModel.stopScan() },
                    modifier = Modifier.weight(1f),
                    enabled = scanState is BleScanState.Scanning
                ) {
                    Text("⏹️ 스캔 중지")
                }
            }

            // 스캔 결과 또는 제어 버튼
            when (val state = scanState) {
                BleScanState.Idle -> {
                    Text("스캔 대기 중...", style = MaterialTheme.typography.bodyMedium)
                }
                is BleScanState.Scanning -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("스캔 중...")
                    }

                    Text(
                        "📡 발견된 기기: ${state.results.size}개",
                        style = MaterialTheme.typography.titleSmall
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.results) { scanResult ->
                            DeviceCard(
                                device = scanResult.device,
                                rssi = scanResult.rssi,
                                onClick = { viewModel.connect(scanResult.device) }
                            )
                        }
                    }
                }
                BleScanState.Stopped -> {
                    Text("스캔 완료", style = MaterialTheme.typography.bodyMedium)
                }
                is BleScanState.Error -> {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))) {
                        Text(
                            "❌ 스캔 에러: ${state.message}",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Red
                        )
                    }
                }
            }

            // 연결 후 제어 버튼들
            if (connectionState is BleConnectionState.Ready) {
                Divider()

                Text("🎮 제어", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.sendData("01") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📤 Write")
                    }

                    Button(
                        onClick = { viewModel.readSensorValue() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📖 Read")
                    }
                }


                Button(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("🔌 연결 해제")
                }
            }
        }
    }
}

@Composable
fun StatusCard(title: String, status: String) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun DeviceCard(device: BluetoothDevice, rssi: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val deviceName = try {
                device.name ?: "Unknown Device"
            } catch (e: SecurityException) {
                "Unknown Device"
            }

            val deviceAddress = try {
                device.address
            } catch (e: SecurityException) {
                "[권한 필요]"
            }
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "주소: $deviceAddress",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "신호: $rssi dBm",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    rssi > -60 -> Color.Green
                    rssi > -80 -> Color.Yellow
                    else -> Color.Red
                }
            )
        }
    }
}

