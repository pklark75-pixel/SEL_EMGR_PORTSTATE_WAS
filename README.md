# SEL_EMGR_PORTSTATE_WAS

HSBC Korea 펀드 잔고 안내 이메일 자동화 시스템 — **WAS / WLP WAR 배포 버전**.  
[SEL_EMGR_PORTSTATE](https://github.com/pklark75-pixel/SEL_EMGR_PORTSTATE)(Spring Boot 내장 Tomcat)를 WebSphere Application Server(WAS Traditional) 및 WebSphere Liberty Profile(WLP)에 배포할 수 있도록 WAR 패키징으로 전환한 프로젝트입니다.

## 원본 프로젝트와의 차이점

| 항목 | SEL_EMGR_PORTSTATE (원본) | SEL_EMGR_PORTSTATE_WAS (이 프로젝트) |
|---|---|---|
| 패키징 | JAR (내장 Tomcat) | **WAR** (외부 컨테이너) |
| 실행 방식 | `mvn spring-boot:run` | WAS/WLP에 WAR 배포 또는 `mvn liberty:run` (로컬) |
| Tomcat 의존성 | 포함 (embedded) | **provided** (컨테이너 제공) |
| `PfsWebApplication` | `main()` 전용 | `SpringBootServletInitializer` 확장 |
| `web.xml` | 없음 | 추가 (Jakarta EE 10 / WAS Traditional 호환) |
| `server.xml` | 없음 | WLP 배포 설정 (liberty-maven-plugin 연동) |
| 로컬 WLP 기동 | 없음 | `mvn liberty:run` (C:\SELPRG\wlp 고정 경로 사용) |
| 인증 | 없음 | **Spring Security 폼 로그인** |
| 모니터링 | 없음 | **Spring Boot Actuator** (`/actuator/health`) |

## 기술 스택

| 항목 | 내용 |
|---|---|
| 런타임 | **Java 21 LTS** |
| 프레임워크 | **Spring Boot 3.5.3** + Spring MVC + Thymeleaf |
| 보안 | **Spring Security 6** (BCrypt, 폼 로그인) |
| 모니터링 | **Spring Boot Actuator** |
| 서블릿 API | **Jakarta EE 10** (jakarta.*) |
| 서블릿 컨테이너 | WAS Traditional 9.x 또는 **WLP / Open Liberty** |
| WLP 피처 | `servlet-6.0` |
| 데이터베이스 | PostgreSQL (raw JDBC, pfs-jdk.properties 기반) |
| 빌드 산출물 | `portstate-was-1.0.0-SNAPSHOT.war` |

## 프로젝트 구조

```
SEL_EMGR_PORTSTATE_WAS/
├── src/main/java/com/hsbc/sel/emgr/
│   ├── PfsWebApplication.java              # SpringBootServletInitializer 확장
│   ├── config/
│   │   ├── PfsConfig.java                  # 서비스 빈 등록
│   │   ├── SecurityConfig.java             # Spring Security 설정
│   │   └── PfsProperties.java              # pfs-jdk.properties 로더
│   ├── web/PfsController.java              # Spring MVC 컨트롤러
│   ├── service/                            # 순수 Java 비즈니스 로직
│   ├── model/                              # POJO 모델
│   ├── security/                           # AES 암호화 유틸
│   └── jdk/                               # 독립 실행 유틸 (HttpServer, 테스트 도구)
├── src/main/resources/
│   ├── application.yml                     # Spring Boot 설정
│   └── templates/pfs.html                  # Thymeleaf 템플릿
├── src/main/webapp/
│   └── WEB-INF/
│       └── web.xml                         # Jakarta EE 10 (web-app 6.0)
├── src/main/liberty/
│   └── config/
│       └── server.xml                      # WLP 피처 및 앱 설정
├── files/
│   ├── init-schema.sql                     # PostgreSQL 초기 스키마
│   ├── pfs-jdk.properties                  # 로컬 설정 (gitignore)
│   └── template/                           # 이메일 HTML 템플릿
└── pom.xml
```

## 빌드

```powershell
# WAR 파일 생성 (target/portstate-was-1.0.0-SNAPSHOT.war)
mvn clean package -DskipTests
```

## 데이터베이스 설정

### 스키마 생성

```powershell
psql -U postgres -c "CREATE DATABASE pfsdb;"
psql -U postgres -d pfsdb -f "files\init-schema.sql"
```

생성 테이블 (`loan` 스키마):

| 테이블 | 설명 |
|---|---|
| `emgr_smtp_info_q` | 이메일 발송 큐 (source_app, fund_count 포함) |
| `emgr_smtp_recv_list_q` | 수신자 목록 |
| `emgr_smtp_files_q` | 첨부 HTML 파일 (BYTEA) |
| `emgr_upload_audit` | 업로드 처리 이력 |

### 설정 파일

`files/pfs-jdk.properties` 생성 (gitignore 적용):

```properties
pfs.baseDir=C:\<경로>\SEL_EMGR_PORTSTATE_WAS\files

db.queue.enabled=true
db.url=jdbc:postgresql://localhost:5432/pfsdb
db.user=postgres
db.password=<비밀번호>
db.driverClass=org.postgresql.Driver

db.queue.filesTable=loan.emgr_smtp_files_q
db.queue.recvTable=loan.emgr_smtp_recv_list_q
db.queue.infoTable=loan.emgr_smtp_info_q
db.audit.uploadTable=loan.emgr_upload_audit

mail.appName=eManager
mail.sender=HSBC Korea <info@kr.hsbc.com>
mail.title=Portfolio Statement Upload Notification
```

### DB 비밀번호 암호화 (선택)

`db.password` 대신 AES-256 암호화된 값을 사용할 수 있습니다.

```properties
# db.password 대신 암호화된 값 지정
db.password.enc=<AES 암호화된 Base64 문자열>

# 복호화 키 소스 (우선순위 순, 하나만 설정)
db.password.keyEnv=PFS_DB_KEY          # 환경변수 (기본)
db.password.keyFile=files/pfs-db.key   # 키 파일
db.password.keyWinCred=DB_KEY          # Windows Credential Manager
```

```powershell
# 암호화된 비밀번호 생성 (키는 환경변수 PFS_DB_KEY 또는 직접 입력)
mvn exec:java -Dexec.mainClass=com.hsbc.sel.emgr.jdk.PfsPasswordCryptoTool `
    -Dexec.args="encrypt <평문비밀번호>"
```

## 보안 (Spring Security)

폼 기반 로그인이 적용되어 있습니다.

| 항목 | 기본값 |
|---|---|
| 로그인 URL | `/pfs/login` |
| 기본 계정 | `pfsadmin` / `pfs1234` |
| 비밀번호 인코딩 | BCrypt |
| 로그아웃 URL | `/pfs/logout` (POST) |
| 인증 제외 | `/actuator/health`, `/actuator/info` |

### 계정 설정

계정 정보는 `application.yml`에서 관리하며 **환경변수로 오버라이드** 가능합니다.

```yaml
# application.yml (기본값)
app:
  security:
    username: ${PFS_APP_USER:pfsadmin}
    password: ${PFS_APP_PASSWORD:pfs1234}
```

| 환경변수 | 설명 | 기본값 |
|---|---|---|
| `PFS_APP_USER` | 로그인 계정명 | `pfsadmin` |
| `PFS_APP_PASSWORD` | 로그인 비밀번호 (평문) | `pfs1234` |

**WLP 배포 시 환경변수 적용 예:**

```
# jvm.options 또는 서버 시작 전 환경변수 설정
-DPFS_APP_USER=운영계정
-DPFS_APP_PASSWORD=운영비밀번호
```

> 운영 환경에서는 반드시 환경변수로 기본값을 교체하거나, `SecurityConfig.java`의 `userDetailsService()`를 DB 기반 인증으로 전환하세요.

## WLP (WebSphere Liberty Profile) 배포

### WLP 설치 경로

로컬 WLP는 `C:\SELPRG\wlp`에 고정 설치됩니다 (`mvn clean` 후에도 유지).

### 방법 A — 로컬 개발 테스트 (liberty-maven-plugin)

```powershell
# 1. files/pfs-jdk.properties 생성 (DB 설정 포함)

# 2. Liberty 기동 (WAR 자동 빌드·배포 포함)
mvn liberty:run

# 접속: http://localhost:9080/pfs  (로그인 필요: pfsadmin / pfs1234)

# 3. 종료: Ctrl+C 또는
mvn liberty:stop
```

**JVM 옵션 자동 전달:** `-Dpfs.baseDir=<프로젝트>/files`

**WLP 피처** (`src/main/liberty/config/server.xml`):

```xml
<featureManager>
    <feature>servlet-6.0</feature>
</featureManager>
```

**클래스로더 설정** (WLP는 `server.xml`로 자동 적용):

```xml
<webApplication id="portstate-was"
                location="portstate-was-1.0.0-SNAPSHOT.war"
                contextRoot="/pfs">
    <classloader delegation="parentLast"/>
</webApplication>
```

> `parentLast` 없으면 WAS/WLP 내장 `slf4j`, `spring-core`와 충돌해  
> `ClassCastException` 또는 `NoSuchMethodError`가 발생합니다.

---

### 방법 B — 운영 WLP 서버 배포 (수동)

#### 1. WAR 빌드

```powershell
mvn clean package -DskipTests
# 산출물: target/portstate-was-1.0.0-SNAPSHOT.war
```

#### 2. WAR 배포

```powershell
# WAR 파일을 WLP apps 디렉토리에 복사
Copy-Item target\portstate-was-1.0.0-SNAPSHOT.war `
    "$env:WLP_INSTALL_DIR\usr\servers\<serverName>\apps\"
```

#### 3. server.xml 설정

```xml
<featureManager>
    <feature>servlet-6.0</feature>
</featureManager>

<webApplication id="portstate-was"
                location="${server.config.dir}/apps/portstate-was-1.0.0-SNAPSHOT.war"
                contextRoot="/pfs">
    <classloader delegation="parentLast"/>
</webApplication>
```

#### 4. JVM 옵션 설정

`${wlp.install.dir}/usr/servers/<serverName>/jvm.options`:

```
-Dpfs.baseDir=/opt/pfs/files
```

#### 5. 서버 시작 및 접속

```powershell
& "$env:WLP_INSTALL_DIR\bin\server.bat" start <serverName>
# 접속: http://<host>:9080/pfs
```

## WAS Traditional (9.x) 배포

### 1. 관리 콘솔에서 배포

1. 관리 콘솔 접속 → **Applications → New Application → New Enterprise Application**
2. `portstate-was-1.0.0-SNAPSHOT.war` 업로드
3. Context root: `/pfs` 설정
4. 저장 후 애플리케이션 시작
5. 접속: `http://<host>:<port>/pfs`

### 2. wsadmin 스크립트로 배포 (선택)

```tcl
$AdminApp install portstate-was-1.0.0-SNAPSHOT.war \
    {-contextroot /pfs -appname portstate-was}
$AdminConfig save
$AdminApp start portstate-was
```

### 3. 클래스로더 설정

WAS Traditional의 `ibm-web-ext.xml` 스키마는 `<classloader>` 요소를 지원하지 않습니다.  
클래스로더 위임 전략은 **관리 콘솔에서 수동 설정**해야 합니다.

**관리 콘솔 경로:**  
Applications → 해당 앱 선택 → Class loading and update detection  
→ Class loader order: **Classes loaded with local class loader first (parent last)**

## 주요 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/pfs` | 필요 | 메인 대시보드 |
| `POST` | `/pfs/upload-zip` | 필요 | 배치 ZIP 업로드 및 자동 처리 |
| `GET` | `/pfs/html-file?id=&app=&name=` | 필요 | DB 저장 HTML 미리보기 |
| `GET` | `/pfs/actuator/health` | 불필요 | 헬스체크 |
| `GET` | `/pfs/actuator/metrics` | 필요 | 메트릭 |

## 배치 처리 흐름

```
ZIP 업로드 (POST /pfs/upload-zip)
  └─ 파일 수 검증: 4개 또는 6개
  └─ 파일명 패턴: (KRHSBC|KRHRED)_UT_YYYYMMDD_1_(CustAttr|CustSubs|EmailCustMast).txt
  └─ 고객별 HTML 렌더링
  └─ PostgreSQL 큐 테이블 INSERT
       ├─ emgr_smtp_files_q  ← HTML (BYTEA)
       ├─ emgr_smtp_recv_list_q ← 수신자 이메일
       └─ emgr_smtp_info_q   ← 메타데이터 (source_app, fund_count)
  └─ 업로드 이력 저장
```

## 유틸리티 도구

```powershell
# DB 연결 테스트
mvn exec:java -Dexec.mainClass=com.hsbc.sel.emgr.jdk.PfsDbConnectionTest

# DB 비밀번호 AES 암호화 / 복호화
mvn exec:java -Dexec.mainClass=com.hsbc.sel.emgr.jdk.PfsPasswordCryptoTool `
    -Dexec.args="encrypt <평문>"
mvn exec:java -Dexec.mainClass=com.hsbc.sel.emgr.jdk.PfsPasswordCryptoTool `
    -Dexec.args="decrypt <암호화된값>"
```
