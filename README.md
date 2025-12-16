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

이 시연 프로그램에서 개발자에게 꼭 필요한 4가지 핵심 요소는 다음과 같습니다. CADP 기능을 이해하고 커스터마이징하기 위해 다음 파일들을 우선적으로 확인하시기 바랍니다.

1.  **pom.xml (CADP 의존성)**
    *   CADP 라이브러리(`CADP_for_JAVA`) 사용을 위한 Maven 의존성이 정의되어 있습니다.
    ```xml
    <dependency>
        <groupId>io.github.thalescpl-io.cadp</groupId>
        <artifactId>CADP_for_JAVA</artifactId>
        <version>8.18.1.000</version>
    </dependency>
    ```

2.  **[src/main/resources/cadp.properties](src/main/resources/cadp.properties)**
    *   CipherTrust Manager(CM)와의 연동 정보 및 암호화 동작을 정의하는 설정 파일입니다.

3.  **[src/main/java/com/example/CadpClient.java](src/main/java/com/example/CadpClient.java)**
    *   복잡한 CADP 초기화 및 호출 로직을 캡슐화하여 손쉽게 구현을 돕는 Helper 싱글톤 클래스입니다.

4.  **[src/main/java/com/example/servlet/EmployeeCadpDecServlet.java](src/main/java/com/example/servlet/EmployeeCadpDecServlet.java)**
    *   `CadpClient.getInstance().dec(ssnRaw)`를 호출하여 실제로 데이터를 복호화하는 예제 코드가 구현되어 있습니다.

> **참고**: CRDP(CipherTrust RESTful Data Protection)의 경우도 1번(CADP 의존성)을 제외하고는 이와 동일한 구조를 가집니다. `crdp.properties`, `CrdpClient.java`, 그리고 `EmployeeCrdpDecServlet.java`를 참고하시면 됩니다.

## 사전 요구 사항 (Prerequisites)

이 프로젝트를 실행하기 위해서는 다음 도구들이 설치되어 있어야 합니다.

*   **Docker & Docker Compose**: 데이터베이스(MySQL) 및 웹 애플리케이션 서버(Tomcat) 컨테이너 실행용.
*   **Java 11 이상**: 애플리케이션 빌드용 (컨테이너 내부가 아닌 로컬 빌드 시).
*   **Maven**: 프로젝트 의존성 관리 및 패키징용.

## 설치 및 실행 방법 (Installation)

### 1. 프로젝트 설정

샘플 데이터를 압축해제하고 tomcat과 mysql 컨테이너를 생성합니다.
```bash
cd scripts/sample_data
tar zxvf employees.tar.gz

cd ../tomcat_mysql_docker
docker-compose up -d 또는 docker compose up -d
```

### 2. 데이터베이스 및 워크스페이스 준비
터미널에서 제공된 스크립트를 사용하여 Maven 빌드를 수행하고, 생성된 WAR 파일을 배포 경로로 복사합니다.
```bash
cd scripts
./01_setup_workspace.sh
```
이 스크립트는 다음 작업을 수행합니다:
1.  `mvn clean package`를 실행하여 `java-db-docker` 프로젝트 빌드.
2.  빌드된 `ROOT.war` (또는 압축 해제된 폴더)를 `tomcat_mysql_docker/target/ROOT`로 배포.

**참고**:  docker와 공유되는 ROOT 폴더의 권한을 미리 변경합니다. (sudo chown -R ubuntu:ubuntu ROOT)

### 3. 데이터 초기 암호화
평문 데이터를 암호화하여 MySQL 데이터베이스에 저장합니다.
```bash
./i01_export_employees.sh   # 평문 데이터를 MySQL 데이터베이스에서 추출
./i02_convert.sh            # crdp-file-converter를 사용하여 암호화, crdp service 접속 필요
./i03_reload_employees.sh   # 암호화된 데이터를 MySQL 데이터베이스에 저장
./run_sql.sh < sample_data/02_load_data.sql # 초기 평문 데이터가 없는 경우 실행
```

## 설정 (Configuration)

애플리케이션이 CRDP 및 CADP 서비스와 올바르게 통신하기 위해 다음 설정이 필요합니다.

### 1. CRDP 설정
CRDP 라이브러리 설정은 `src/main/resources/crdp.properties` 파일에 올바른 연결 정보를 입력해야 합니다.

### 2. CADP 설정
CADP 라이브러리 설정은 `src/main/resources/cadp.properties` 파일에 올바른 연결 정보를 입력해야 합니다.

### 3. 데이터베이스 연결 설정
기본적으로 DB 연결 정보는 서블릿 코드 내에 설정되어 있거나 컨테이너 환경 변수를 따릅니다.
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
