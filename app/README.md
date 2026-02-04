# BLE Test App

**jdw-bluetooth** 라이브러리를 테스트하기 위한 간단한 Android 앱입니다.  
**nRF Connect Mobile** 앱과 함께 사용하면 BLE 통신을 쉽게 테스트할 수 있습니다.

---

## 🚀 빠른 시작

### 1. 프로젝트 실행
1. Android Studio에서 프로젝트 열기
2. Gradle 동기화 (자동 실행)
3. 실제 Android 기기에 연결 (에뮬레이터 ❌)
4. **▶️ Run** 버튼 클릭

### 2. nRF Connect Mobile 준비
1. 다른 Android 기기에 **nRF Connect** 앱 설치 ([Google Play](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp))
2. nRF Connect 실행 → **Advertiser** 탭 선택
3. **New advertising packet** 생성
4. Advertising 시작

### 3. BLE 통신 테스트
1. 테스트 앱에서 **"🔍 스캔 시작"** 클릭
2. nRF Connect 기기 선택 → 자동 연결
3. **📤 Write** / **📖 Read** / **Notification** 테스트

---

## 📱 nRF Connect로 테스트하는 방법

### nRF Connect를 BLE 서버로 사용
1. **Advertiser** 탭 → 광고 시작
2. 테스트 앱에서 연결
3. **Server** 탭에서 Service/Characteristic 추가
4. 테스트 앱의 Write/Read 동작 확인

### nRF Connect를 클라이언트로 사용 (반대 테스트)
1. 테스트 앱이 광고를 시작하도록 라이브러리 설정
2. nRF Connect에서 스캔 → 연결
3. Characteristic에 Write/Read/Notify 테스트

---

## 🎯 구현된 기능

| 기능 | 설명 |
|-----|------|
| **🔍 스캔** | 주변 BLE 기기 검색 (기기명, 주소, RSSI 표시) |
| **🔗 연결** | 기기 클릭하여 자동 연결 |
| **📤 Write** | 데이터 전송 (기본: "01" 전송) |
| **📖 Read** | Characteristic 값 읽기 |
| **🔔 Notification** | 실시간 데이터 수신 (자동 구독) |
| **🔌 연결 해제** | GATT 연결 종료 |

---

## 🔧 UUID 설정

기본 UUID는 심박수 센서(Heart Rate Service) 기준입니다.  
다른 BLE 기기를 사용하려면 `MyBleConfig.kt`를 수정하세요:

```kotlin
data class MyBleConfig(
    override val serviceUuid: String = "0000180d-0000-1000-8000-00805f9b34fb",
    override val notifyCharUuid: String = "00002a37-0000-1000-8000-00805f9b34fb",
    // ...
)
```

### nRF Connect에서 UUID 확인하기
1. nRF Connect로 기기 스캔 및 연결
2. **Services** 탭에서 UUID 확인
3. 복사하여 `MyBleConfig.kt`에 붙여넣기

---

## 📊 Logcat으로 디버깅

Android Studio Logcat에서 `tag:BLE` 필터링:

```
🔍 스캔 시작
🔗 연결 시도: Nordic_UART (AA:BB:CC:DD:EE:FF)
📨 Notification 수신 from 00002a37-...: 01 48 00
✅ Write 성공!
📖 센서 값: 01 02 03 04
```

---

## 🐛 문제 해결

### 스캔해도 기기가 안 보여요
- nRF Connect에서 Advertising이 시작되었는지 확인
- 권한을 모두 허용했는지 확인
- Android 9 이하: 위치 서비스(GPS) 켜기

### 연결은 되는데 Write/Read가 안 돼요
- nRF Connect **Server** 탭에서 Service/Characteristic이 추가되었는지 확인
- UUID가 일치하는지 확인
- Characteristic의 속성(WRITE, READ, NOTIFY)이 올바른지 확인

### "Unresolved reference 'bluetooth'" 에러
```
File > Sync Project with Gradle Files
```

---

## 📁 프로젝트 구조

```
app/src/main/java/com/jdw/ble_test/
├── MainActivity.kt        # Compose UI
├── BleViewModel.kt        # BLE 로직 (스캔/연결/Read/Write/Notification)
└── MyBleConfig.kt         # UUID 및 BLE 설정
```

---

## 📚 참고

- **jdw-bluetooth 라이브러리**: https://github.com/Dalso13/jdw-bluetooth
- **nRF Connect Mobile**: https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-Mobile
- **BLE 기본 개념**: https://developer.android.com/guide/topics/connectivity/bluetooth-le

---

## 💡 팁

- 테스트는 **실제 Android 기기 2대**로 하는 것이 가장 편리합니다
  - 기기 1: 이 테스트 앱 실행
  - 기기 2: nRF Connect 실행
- nRF Connect의 **UART Service** 템플릿을 사용하면 간편합니다
- Logcat을 항상 켜두고 BLE 통신 과정을 확인하세요

---

이 앱으로 라이브러리가 정상 동작하는지 확인한 후 실제 프로젝트에 적용하세요! 🚀

