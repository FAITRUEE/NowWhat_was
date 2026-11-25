<div align="center">

# NowWhat — 지금 뭐부터 하지? (Backend)

**할 일을 마감·중요도·예상 시간으로 점수화해 오늘 먼저 할 일을 줄 세우는 서비스의 서버입니다.<br>"AI 재정렬"을 누르면 상위 후보를 로컬 LLM이 다시 보고 순서와 이유를 다듬으며, 실패하면 규칙 기반 순위로 돌아갑니다.**

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security_+_JWT-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![H2](https://img.shields.io/badge/JPA_·_H2-1E4E8C?style=flat-square)
![Ollama](https://img.shields.io/badge/Ollama_·_EXAONE_3.5-000000?style=flat-square&logo=ollama&logoColor=white)
![Tests](https://img.shields.io/badge/tests-51_passing-2E7D32?style=flat-square)

[프론트엔드 저장소 (NowWhat_web)](https://github.com/FAITRUEE/NowWhat_web) · 1인 풀스택 · 2026 여름 영남대 생성형 AI 서비스 플랫폼 활용 교육

<img src="https://raw.githubusercontent.com/FAITRUEE/NowWhat_web/main/docs/screenshots/dashboard.png" alt="NowWhat 대시보드" width="860">

| 할 일 상세 | 가중치 설정 | 팀 칸반 보드 |
|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/FAITRUEE/NowWhat_web/main/docs/screenshots/task-detail.png" alt="할 일 상세" width="280"> | <img src="https://raw.githubusercontent.com/FAITRUEE/NowWhat_web/main/docs/screenshots/weights.png" alt="가중치 설정" width="280"> | <img src="https://raw.githubusercontent.com/FAITRUEE/NowWhat_web/main/docs/screenshots/team-board.png" alt="팀 칸반 보드" width="280"> |

</div>

<br>

## 설계에서 신경 쓴 것

| 무엇을 | 어떻게 |
|---|---|
| 점수는 늘 지금 기준으로 | 조회할 때마다 현재 시각으로 긴급도를 다시 계산 — 저장된 점수가 낡지 않음 |
| LLM은 보조로만 | 순서는 규칙 점수가 정하고, "AI 재정렬" 요청 때만 상위 후보(기본 5개)를 LLM에 보냄. 서버가 꺼져 있거나 응답을 못 읽으면 규칙 기반 순위로 폴백 |
| 응답 형식 깨짐 대비 | Ollama JSON 모드로 받아 파싱하고, 역할 추천 분배는 응답의 업무 ID가 실제 후보와 맞는지까지 검사한 뒤 반영 |
| 권한은 역할별로 | 칸반 상태 변경은 팀원 누구나, 내용 수정·삭제는 담당자만 — 기존 개인 업무 권한 체크를 재사용 |
| 인증 실패는 401로 | 토큰이 없거나 만료되면 401을 돌려줘, 프론트가 받아서 로그인 화면으로 보냄 (권한 없음은 403) |
| 테스트는 실 DB와 분리 | 인메모리 H2로 51개 테스트 — `bootRun`이 떠 있어도 항상 통과 |

<br>

## 핵심 기능

- **할 일 CRUD**: 제목, 설명, 마감일, 예상 소요시간, 중요도(1~5) 관리
- **우선순위 자동 계산**: 규칙 기반 스코어링으로 할 일 정렬 (조회 시점마다 최신 시각 기준으로 재계산)
- **가중치 커스터마이징**: 긴급도/중요도/소요시간 가중치를 사용자가 직접 조정, 변경 시 전체 재계산
- **완료 처리 및 이력 관리**: 완료 시 실제 소요시간 기록, 기간별 이력 조회
- **LLM 기반 재정렬**: 점수 상위 할 일들(기본 5개)을 로컬 LLM(Ollama)에 전달해 자연어로 재정렬 + 추천 이유 생성, 실패 시 규칙 기반 순위로 자동 폴백
- **팀 협업**: 팀 생성/멤버 초대, 담당자를 지정한 업무 생성, LLM 기반 업무 그룹화("역할 추천 분배" — 이미 만들어진 업무들을 우선순위/성격이 비슷한 묶음으로 재편성, 실패 시 라운드로빈 폴백), 담당자 재배정("나로 바꾸기"), 팀원 누구나 편집 가능한 공유 칸반보드(할 일/진행 중/완료)

## 기술 스택

| 영역 | 기술 |
|---|---|
| 언어/프레임워크 | Java 21, Spring Boot 4.1 |
| 빌드 도구 | Gradle |
| DB | H2 (파일 기반, `./data/nowwhat`, 개발용) |
| ORM | Spring Data JPA / Hibernate |
| 인증 | Spring Security + JWT (jjwt) |
| LLM 연동 | Ollama REST API (로컬 실행) |

## 우선순위 알고리즘

```
score = w1 * urgency_score + w2 * importance_score + w3 * effort_score
```

- `urgency_score`: 마감일까지 남은 시간 기반 지수 감쇠 (마감이 가까울수록 1에 수렴)
- `importance_score`: 사용자가 입력한 중요도(1~5)를 0~1로 정규화
- `effort_score`: 예상 소요시간이 짧을수록 가산점
- `w1(urgency) / w2(importance) / w3(effort)`: 사용자별 가중치, 기본값 `0.5 / 0.3 / 0.2`, `PUT /api/users/weights`로 조정 가능

## 팀 협업

`Task`에 담당자(`user`)와 별개로 선택적 `team` 연결이 있어, 팀 업무도 개인 업무와 완전히 같은 CRUD/우선순위 계산 경로를 그대로 탑니다.

- **업무 생성**: `POST /api/teams/{id}/tasks`로 담당자를 지정해 업무를 만듭니다. LLM은 여기 관여하지 않습니다.
- **역할 추천 분배**(`POST /api/teams/{id}/organize`): 이미 만들어진 팀의 미완료 업무들을 로컬 LLM(Ollama)에 보내 우선순위·성격이 비슷한 것끼리 그룹(`TaskBundle`)으로 재편성합니다. **담당자는 절대 바뀌지 않습니다** — 순전히 보기 좋게 묶어주는 기능입니다. LLM이 실패하거나 응답이 스키마에 안 맞으면 라운드로빈으로 그룹을 나누는 폴백이 동작합니다.
- **담당자 재배정**(`PATCH /api/tasks/{id}/reassign`, "나로 바꾸기"): 팀원이면 누구나 다른 사람이 담당하던 업무를 자신에게로 옮길 수 있습니다. 선착순 개념이 아니라 언제든 가능한 단순 소유권 이전입니다.
- **공유 칸반보드**(`GET /api/teams/{id}/board`): 팀의 전체 업무를 담당자 이메일·점수와 함께 반환합니다. 상태 변경(`PATCH /api/tasks/{id}/status`)은 담당자가 아니어도 같은 팀 멤버면 누구나 할 수 있습니다 — 칸반보드는 팀 전체가 같이 쓰는 화면이기 때문입니다. 단, 업무 내용 수정(`PUT`)·삭제(`DELETE`)는 현재 담당자만 가능합니다(기존 개인 업무 권한 체크를 그대로 재사용).

## API 엔드포인트

| 기능 | Method | Path |
|---|---|---|
| 회원가입 | POST | `/api/auth/signup` |
| 로그인 | POST | `/api/auth/login` |
| 가중치 조회 | GET | `/api/users/weights` |
| 가중치 수정 | PUT | `/api/users/weights` |
| 할 일 등록 | POST | `/api/tasks` |
| 할 일 목록 (view=today\|week\|all) | GET | `/api/tasks` |
| 할 일 상세 | GET | `/api/tasks/{id}` |
| 할 일 수정 | PUT | `/api/tasks/{id}` |
| 할 일 삭제 | DELETE | `/api/tasks/{id}` |
| 할 일 완료 처리 | PATCH | `/api/tasks/{id}/complete` |
| 할 일 상태 변경 (TODO\|IN_PROGRESS\|DONE, 팀원이면 누구나) | PATCH | `/api/tasks/{id}/status` |
| 할 일 담당자를 나로 변경 ("나로 바꾸기", 팀원이면 누구나) | PATCH | `/api/tasks/{id}/reassign` |
| 캘린더 범위 조회 (from/to, 완료 포함) | GET | `/api/tasks/calendar` |
| LLM 기반 재정렬 | POST | `/api/tasks/rerank` |
| 완료 이력 조회 (period=week\|month) | GET | `/api/tasks/history` |
| 팀 생성 | POST | `/api/teams` |
| 내 팀 목록 | GET | `/api/teams` |
| 팀 상세 (멤버 목록 포함) | GET | `/api/teams/{id}` |
| 팀원 추가 (가입된 이메일, 팀장만) | POST | `/api/teams/{id}/members` |
| 팀 업무 생성 (담당자 지정) | POST | `/api/teams/{id}/tasks` |
| 팀 업무 LLM 그룹화 ("역할 추천 분배") | POST | `/api/teams/{id}/organize` |
| 그룹화된 업무 묶음 조회 | GET | `/api/teams/{id}/bundles` |
| 팀 공유 보드 조회 (담당자·점수 포함) | GET | `/api/teams/{id}/board` |

`/api/auth/**`를 제외한 모든 엔드포인트는 `Authorization: Bearer <JWT>` 헤더가 필요합니다.

## 시작하기

### 사전 요구사항

- Java 21
- [Ollama](https://ollama.com) (LLM 재정렬 기능 사용 시)
  ```bash
  ollama pull exaone3.5:7.8b
  ollama serve
  ```
  Ollama가 실행 중이지 않아도 다른 기능에는 영향 없이, 재정렬 요청만 규칙 기반 순서로 자동 폴백됩니다.

### 실행

```bash
./gradlew bootRun
```

서버는 `http://localhost:8080`에서 실행되며, DB는 파일 기반(H2)이라 재시작해도 데이터가 유지됩니다 (`./data/nowwhat.mv.db`). 첫 실행 시 `DemoDataInitializer`가 데모 계정(`demo@nowwhat.com` / `demo1234`)을 자동 생성합니다. H2 콘솔은 `http://localhost:8080/h2-console`에서 확인 가능합니다 (JDBC URL: `jdbc:h2:file:./data/nowwhat`).

### 빌드 / 테스트

```bash
./gradlew build          # 컴파일 + 테스트
./gradlew test           # 테스트만 실행
```

단위 테스트(`PriorityScoreCalculatorTest`, `TaskRerankServiceTest`, `TeamAssignmentServiceTest`) + 컨트롤러별 MockMvc 통합 테스트(회원가입/로그인, 할 일 CRUD/완료/이력, 가중치, 팀 생성/멤버/업무/그룹화/재배정, CORS, 인증 없는 요청 401 등) 51개가 있습니다. 테스트는 `src/test/resources/application.yml`로 별도 인메모리 H2(`jdbc:h2:mem:nowwhat-test`)를 써서 위의 파일 기반 dev DB와 완전히 격리되어 있습니다 — `bootRun`이 떠 있어도 `./gradlew test`가 파일 락 충돌 없이 항상 통과합니다.

## 프로젝트 구조

도메인 기반 패키징을 사용합니다.

```
src/main/java/com/nowwhat/
├── global/                # 공통 설정, 예외 처리, 보안
│   ├── config/
│   ├── exception/
│   ├── security/
│   └── common/
└── domain/
    ├── user/              # 인증, 사용자 가중치
    ├── task/               # 할 일 CRUD, 우선순위 계산, LLM 재정렬, 상태 변경/재배정
    ├── taskhistory/        # 완료 이력
    └── team/                # 팀, 팀원, 업무 묶음(TaskBundle), LLM 기반 업무 그룹화
```

각 도메인 패키지는 `entity / repository / service / controller / dto` 하위 구조를 가집니다.

## 참고 사항

- `application.yml`의 `jwt.secret`은 로컬 개발용 플레이스홀더입니다(`${JWT_SECRET:...}`). 이 플레이스홀더 그대로 뜨면 `JwtTokenProvider`가 부팅 로그에 WARN을 남깁니다 — 배포 전에는 반드시 `JWT_SECRET` 환경 변수로 실제 랜덤 값을 넣어주세요.
- CORS는 `SecurityConfig`의 `CorsConfigurationSource`로 설정되며, 허용 origin은 `application.yml`의 `app.cors.allowed-origins`(기본값 `http://localhost:5173`)입니다. 프론트를 다른 origin에 배포하면 이 값도 같이 바꿔야 합니다.
- `ollama.base-url` / `ollama.model`도 `application.yml`에서 조정 가능합니다.
