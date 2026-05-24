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
| `web.xml` | 없음 | 추가 (WAS Traditional 호환) |
| `server.xml` | 없음 | WLP 배포 설정 (liberty-maven-plugin 연동) |
| 로컬 WLP 기동 | 없음 | `mvn liberty:run` (Open Liberty 자동 다운로드) |

## 기술 스택

| 항목 | 내용 |
|---|---|
| 런타임 | Java 8 이상 |
| 프레임워크 | Spring Boot 2.7.18 + Thymeleaf |
| 서블릿 컨테이너 | WAS Traditional 8.5.x/9.x 또는 WLP |
| 데이터베이스 | PostgreSQL 18 (raw JDBC) |
| 빌드 산출물 | `portstate-was-1.0.0-SNAPSHOT.war` |

## 프로젝트 구조

```
SEL_EMGR_PORTSTATE_WAS/
├── src/main/java/com/hsbc/sel/emgr/
│   ├── PfsWebApplication.java              # SpringBootServletInitializer 확장
│   ├── config/
│   │   ├── PfsConfig.java
│   │   └── PfsProperties.java
│   ├── web/PfsController.java
│   ├── service/
│   ├── model/
│   └── security/
├── src/main/resources/
│   ├── application.yml
│   └── templates/pfs.html
├── src/main/webapp/
│   └── WEB-INF/
│       └── web.xml                         # WAS Traditional 호환
├── src/main/liberty/
│   └── config/
│       └── server.xml                      # WLP 배포 설정 예시
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

## WLP (WebSphere Liberty Profile) 배포

### 방법 A — 로컬 개발 테스트 (liberty-maven-plugin)

`pom.xml`에 포함된 `liberty-maven-plugin`이 Open Liberty를 자동으로 다운로드하고 기동합니다.  
별도 WLP 설치 없이 바로 사용 가능합니다.

```powershell
# 1. files/pfs-jdk.properties 생성 (DB 설정 포함)
#    - pfs.baseDir는 자동으로 <프로젝트>/files 로 설정됨

# 2. Liberty 기동 (WAR 자동 빌드·배포 포함)
mvn liberty:run

# 접속: http://localhost:9080/pfs

# 3. 종료: Ctrl+C 또는
mvn liberty:stop
```

**JVM 옵션 자동 전달:** `-Dpfs.baseDir=<프로젝트>/files`  
Liberty가 기동되면 해당 경로의 `pfs-jdk.properties`를 읽어 설정을 적용합니다.

**최소 필요 피처** (`src/main/liberty/config/server.xml`):

```xml
<featureManager>
    <feature>servlet-4.0</feature>
    <feature>jsp-2.3</feature>
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

```bash
# WAR 파일을 WLP apps 디렉토리에 복사
cp target/portstate-was-1.0.0-SNAPSHOT.war \
   ${wlp.install.dir}/usr/servers/<serverName>/apps/
```

#### 3. server.xml 설정

```xml
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

```bash
${wlp.install.dir}/bin/server start <serverName>
# 접속: http://<host>:9080/pfs
```

## WAS Traditional (8.5.x / 9.x) 배포

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

`ibm-web-ext.xml`은 클래스로더 외 웹 모듈 설정만 담당합니다:

| 설정 | 값 | 효과 |
|---|---|---|
| `enable-reloading` | `false` | 클래스 변경 감지 비활성화 (성능) |
| `enable-file-serving` | `false` | Spring MVC가 정적 리소스 처리 |
| `enable-directory-browsing` | `false` | 디렉토리 목록 노출 방지 |

> `parentLast` 설정이 없으면 WAS 내장 `slf4j`, `spring-core` 버전과 충돌해  
> `ClassCastException` 또는 `NoSuchMethodError`가 발생할 수 있습니다.

## 주요 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/pfs` | 메인 대시보드 |
| `POST` | `/pfs/upload-zip` | 배치 ZIP 업로드 및 자동 처리 |
| `GET` | `/pfs/html-file?id=&app=&name=` | DB 저장 HTML 미리보기 |

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

# 비밀번호 암호화
mvn exec:java -Dexec.mainClass=com.hsbc.sel.emgr.jdk.PfsPasswordCryptoTool `
    -Dexec.args="encrypt <평문>"
```
