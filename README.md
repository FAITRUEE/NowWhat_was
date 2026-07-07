# NowWhat — 할 일 우선순위 추천 서비스 (Backend)

사용자의 할 일 목록을 마감일·중요도·소요시간 기준으로 분석해, 오늘 무엇부터 해야 하는지 우선순위를 자동으로 추천해주는 개인 생산성 서비스의 백엔드입니다.

## 핵심 기능

- **할 일 CRUD**: 제목, 설명, 마감일, 예상 소요시간, 중요도(1~5) 관리
- **우선순위 자동 계산**: 규칙 기반 스코어링으로 할 일 정렬 (조회 시점마다 최신 시각 기준으로 재계산)
- **가중치 커스터마이징**: 긴급도/중요도/소요시간 가중치를 사용자가 직접 조정, 변경 시 전체 재계산
- **완료 처리 및 이력 관리**: 완료 시 실제 소요시간 기록, 기간별 이력 조회
- **LLM 기반 재정렬**: 점수가 비슷한 상위 할 일들을 로컬 LLM(Ollama)에 전달해 자연어로 재정렬 + 추천 이유 생성, 실패 시 규칙 기반 순위로 자동 폴백

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
| 캘린더 범위 조회 (from/to, 완료 포함) | GET | `/api/tasks/calendar` |
| LLM 기반 재정렬 | POST | `/api/tasks/rerank` |
| 완료 이력 조회 (period=week\|month) | GET | `/api/tasks/history` |

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

단위 테스트(`PriorityScoreCalculatorTest`, `TaskRerankServiceTest`) + 컨트롤러별 MockMvc 통합 테스트(회원가입/로그인, 할 일 CRUD/완료/이력, 가중치, CORS 등) 37개가 있습니다. 테스트는 `src/test/resources/application.yml`로 별도 인메모리 H2(`jdbc:h2:mem:nowwhat-test`)를 써서 위의 파일 기반 dev DB와 완전히 격리되어 있습니다 — `bootRun`이 떠 있어도 `./gradlew test`가 파일 락 충돌 없이 항상 통과합니다.

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
    ├── task/               # 할 일 CRUD, 우선순위 계산, LLM 재정렬
    └── taskhistory/        # 완료 이력
```

각 도메인 패키지는 `entity / repository / service / controller / dto` 하위 구조를 가집니다.

## 참고 사항

- `application.yml`의 `jwt.secret`은 로컬 개발용 플레이스홀더입니다(`${JWT_SECRET:...}`). 이 플레이스홀더 그대로 뜨면 `JwtTokenProvider`가 부팅 로그에 WARN을 남깁니다 — 배포 전에는 반드시 `JWT_SECRET` 환경 변수로 실제 랜덤 값을 넣어주세요.
- CORS는 `SecurityConfig`의 `CorsConfigurationSource`로 설정되며, 허용 origin은 `application.yml`의 `app.cors.allowed-origins`(기본값 `http://localhost:5173`)입니다. 프론트를 다른 origin에 배포하면 이 값도 같이 바꿔야 합니다.
- `ollama.base-url` / `ollama.model`도 `application.yml`에서 조정 가능합니다.
