# OneSync Lab

개인 연구용 OneDrive 폴더 동기화 앱. 설치된 OneSync 7.8.2(`com.ttxapps.onesyncv2`)의 동작을
분석한 결과를 바탕으로, 같은 기능을 독립적으로 구현한다.

- 원격: OneDrive 전용 (Microsoft Graph v1.0)
- 배포: 개인용, 광고/IAP/기능 제한 없음
- 로컬 접근: All Files Access (`MANAGE_EXTERNAL_STORAGE`)

## 빌드

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Gradle 8.11.1 / AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 / minSdk 32.

## 구조

```
core/sync      동기화 판정 엔진 (순수 Kotlin, 안드로이드 의존 없음)
data/db        SQLite 스키마 (원본 앱에서 확인한 DDL)
MainActivity   현재는 상태/권한 화면만
```

## 사용

- `새 폴더 쌍 만들기`로 로컬 폴더와 OneDrive 폴더를 짝지어 **여러 개** 등록할 수 있다.
- 폴더 쌍마다 동기화 방식을 따로 지정하고, `설정`에서 나중에 바꿀 수 있다.
- `하위 폴더와 내용까지 동기화`를 끄면 선택한 폴더의 **최상위 파일만** 동기화한다.
- 폴더 쌍이 2개 이상이면 `전체 동기화`로 등록된 쌍을 순서대로 실행한다.

## 동기화 계약

`SyncPlanner`는 (마지막 동기화 상태, 로컬 스캔, 원격 스캔)을 받아 할 일 목록을 만든다.
아래 표가 이 앱의 계약이며 `SyncPlannerTest`가 고정한다.

| 모드 | 로컬 변경 | 원격 변경 | 로컬 삭제됨 | 원격 삭제됨 | 반대쪽에만 있는 항목 |
|---|---|---|---|---|---|
| TWO_WAY | 업로드 | 다운로드 | 원격 삭제 | 로컬 삭제 | – |
| UPLOAD_ONLY | 업로드 | 무시 | 무시 | 무시 | – |
| UPLOAD_THEN_DELETE | 업로드 후 로컬 삭제 | 무시 | 무시 | 무시 | – |
| UPLOAD_MIRROR | 업로드 | 로컬로 덮어씀 | 원격 삭제 | 다시 업로드 | 원격에서 삭제 |
| DOWNLOAD_ONLY | 무시 | 다운로드 | 무시 | 무시 | – |
| DOWNLOAD_THEN_DELETE | 무시 | 다운로드 후 원격 삭제 | 무시 | 무시 | – |
| DOWNLOAD_MIRROR | 원격으로 덮어씀 | 다운로드 | 다시 다운로드 | 로컬 삭제 | 로컬에서 삭제 |

공통 규칙:

- 양쪽이 모두 바뀌었고 내용이 같으면 전송 없이 링크한다(거짓 충돌 제거). 다르면 `Conflict`.
- 한쪽에만 있는 파일이 반대쪽에 같은 이름·같은 크기로 이미 있으면 링크하고, 다르면 `Conflict`.
- 경로 비교는 NFC 정규화 + 대소문자 무시.
- 실행 순서는 폴더 생성 → 전송/링크 → 삭제(깊은 경로부터).

## 다음 단계

1. Graph 클라이언트 (`me/drive`, `root/delta`, `createUploadSession`)
2. 폴더 쌍 생성 UI (로컬 폴더 선택, 원격 폴더 탐색)
3. 스캐너/실행기 (SyncPlanner 결과를 실제 파일 작업으로)
4. 포그라운드 동기화 서비스 + 알림 + 취소
5. 자동 동기화 스케줄 (AlarmManager + WorkManager) + 즉시 업로드(FileObserver)
6. 동기화 이력 화면

## 인증 (Azure 앱 등록)

앱 실행 → `Azure 앱 등록` 화면에서 필요한 값을 복사하고, 발급된 클라이언트 ID를 붙여넣으면 끝난다.
클라이언트 ID는 런타임에 MSAL 설정 JSON으로 만들어지므로 **ID가 바뀌어도 다시 빌드할 필요가 없다.**

### 자동 등록 (PC, Azure CLI)

Azure CLI가 있으면 1~3단계를 한 번에 처리한다.

```powershell
winget install -e --id Microsoft.AzureCLI   # 최초 1회
powershell -ExecutionPolicy Bypass -File tools\register-app.ps1
```

스크립트가 하는 일:

1. `OneSync Lab` 앱 등록 생성 (같은 이름이 있으면 재사용)
2. `com.dalbear.onesync://auth`를 public client redirect URI로 등록
3. Microsoft Graph 위임 권한 `User.Read`, `Files.ReadWrite.All` 추가
4. 클라이언트 ID를 출력하고 클립보드에 복사

개인 Microsoft 계정은 첫 로그인 때 사용자 동의로 권한이 부여되므로 관리자 동의는 필요 없다.

### 수동 등록 (앱 화면)

Azure Portal → App registrations → New registration:

- Supported account types: 개인 Microsoft 계정
- Platform: **Android**, redirect URI `com.dalbear.onesync://auth`
- API permissions: `User.Read`, `Files.ReadWrite.All` (Delegated)

클라이언트 ID를 저장하면 앱의 `Azure 앱 등록` 화면에 **인증(Authentication)**, **API 권한** 블레이드로
바로 가는 버튼이 생긴다. 포털에서 메뉴를 찾아 들어갈 필요가 없다.

redirect URI를 `msal<클라이언트ID>://auth`가 아니라 고정 스킴으로 둔 이유가 이것이다.
매니페스트의 `BrowserTabActivity` intent-filter와 `AuthConfig.REDIRECT_URI`가 반드시 같아야 한다.

MSAL이 끌어오는 `com.microsoft.device.display:display-mask:0.3.0`은 jcenter 종료 후
Microsoft Duo SDK 피드에만 있어서 `settings.gradle.kts`에 해당 저장소를 추가했다.


