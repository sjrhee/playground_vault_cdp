# Playground CDP

**Playground CDP**는 MySQL 샘플 데이터베이스(Employees)를 활용하여 민감 정보(예: 주민등록번호)에 대한 **CADP(CipherTrust Application Data Protection)** 및 **CRDP(CipherTrust RESTful Data Protection)** 암호화/복호화 통합 기능을 시연하는 Java 웹 애플리케이션입니다.

## 주요 기능 (Features)

*   **직원 목록 조회**: 페이징 처리(100회)된 직원 목록(약 30만 건) 및 신상 정보 조회.
*   **원문 조회 (Original View)**: 데이터베이스에 암호화되어 저장된 상태 그대로의 SSN(주민등록번호) 확인.
    *   [EmployeeOriginalServlet.java](src/main/java/com/example/servlet/EmployeeOriginalServlet.java): 별도의 복호화 로직 없이 DB 데이터를 그대로 JSON으로 변환하여 반환합니다.
*   **CADP 복호화 (CADP Decrypted View)**: Thales CADP Java 라이브러리를 사용하여 애플리케이션 레벨에서 복호화된 SSN 확인.
    *   [EmployeeCadpDecServlet.java](src/main/java/com/example/servlet/EmployeeCadpDecServlet.java): `CadpClient`의 dec를 사용하여 DB에서 가져온 암호화된 SSN을 복호화한 후 반환합니다.
*   **CRDP 복호화 (CRDP Decrypted View)**: Thales CRDP REST API를 호출하여 컨테이너 환경에서 실시간으로 복호화된 SSN 확인.
    *   [EmployeeCrdpDecServlet.java](src/main/java/com/example/servlet/EmployeeCrdpDecServlet.java): `CrdpClient`의 dec를 사용하여 REST API를 호출, SSN을 복호화하여 반환합니다.

## 개발자 사전 숙지 사항

이 시연 프로그램에서 개발자에게 꼭 필요한 핵심 요소는 다음과 같습니다.

1.  **pom.xml (CADP 의존성)**
    *   CADP 라이브러리(`CADP_for_JAVA`) 사용을 위한 Maven 의존성이 정의되어 있습니다.

2.  **[src/main/java/com/example/CadpClient.java](src/main/java/com/example/CadpClient.java)**
    *   **환경 변수 우선 로딩**: 기존 properties 파일 방식 대신, `CADP_`로 시작하는 환경 변수를 우선적으로 읽어오도록 수정되었습니다. 이는 Vault 등 시크릿 관리 도구와의 연동을 위함입니다.

3.  **HashiCorp Vault 연동**
    *   보안 강화를 위해 소스 코드 내의 평문 설정 파일(`cadp.properties`, `crdp.properties`)은 제거되었습니다.
    *   대신 로컬 개발 환경에서도 **HashiCorp Vault**를 통해 시크릿을 주입받아 애플리케이션이 구동됩니다.

## 사전 요구 사항 (Prerequisites)

이 프로젝트를 실행하기 위해서는 다음 도구들이 설치되어 있어야 합니다.

*   **Docker & Docker Compose**: 데이터베이스, 웹 서버, 그리고 **Vault** 컨테이너 실행용.
*   **Java 11 이상**: 애플리케이션 빌드용.
*   **Maven**: 프로젝트 의존성 관리 및 패키징용.

## 설치 및 실행 방법 (Installation)

### 1. 프로젝트 설정 및 시크릿 마이그레이션

이제 단일 스크립트로 Vault 컨테이너를 띄우고, 초기 시크릿을 설정한 뒤, 애플리케이션을 실행합니다.

```bash
# 1. 빌드 및 워크스페이스 준비
cd scripts
./01_setup_workspace.sh

# 2. Vault 실행 및 시크릿 주입/앱 시작
# (이 스크립트는 Vault 구동 -> 시크릿 마이그레이션 -> Tomcat에 시크릿 주입 -> 앱 재시작을 수행합니다)
./migrate_to_vault.sh
```

### 2. 데이터 초기 암호화 (선택 사항)
기존 데이터가 없는 경우 초기 데이터를 적재합니다.
```bash
./i03_reload_employees.sh   # 암호화된 데이터를 MySQL 데이터베이스에 저장
```

## 설정 (Configuration)

### 시크릿 관리 (Secret Management)
이제 `src/main/resources/*.properties` 파일은 사용하지 않습니다. 모든 민감 정보는 **HashiCorp Vault**에 저장되며, 컨테이너 실행 시 환경 변수로 주입됩니다.

*   **Vault UI 접속**: [http://localhost:8200/ui](http://localhost:8200/ui)
    *   **Token**: `roottoken`
    *   **Secret Paths**: `secret/cadp`, `secret/crdp`
*   **환경 변수 목록**:
    *   `CADP_KEY_MANAGER_HOST`, `CADP_REGISTRATION_TOKEN` 등
    *   `CRDP_ENDPOINT`, `CRDP_USER_NAME` 등

### 데이터베이스 연결 설정
*   **URL**: `jdbc:mysql://mysql:3306/mysql_employees`
*   **User**: `testuser`
*   **Password**: `testpassword`

## 사용 방법 (Usage)

모든 컨테이너가 실행 중이고 배포가 완료되면 브라우저를 열고 다음 주소로 접속합니다.

**메인 페이지:**
[http://localhost:8081/index.html](http://localhost:8081/index.html)

*   **직원 신상 정보(원문조회)** 버튼: 암호화된 데이터 확인.
*   **직원 신상 정보(CADP 복호화)** 버튼: CADP를 통해 복호화된 데이터 확인.
*   **직원 신상 정보(CRDP 복호화)** 버튼: CRDP를 통해 복호화된 데이터 확인.

## Kubernetes Migration

Kubernetes 마이그레이션 가이드 및 배포 방법은 [k8s-migration/README.md](k8s-migration/README.md)를 참고하세요.
