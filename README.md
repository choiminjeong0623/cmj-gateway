# cmj-gateway

> ⚠️ **참고용 예시 코드입니다.** 실제 IDRAS 프로젝트 코드가 아니며, 면접 대비를 위해 기억하는 구조와 대화로 확정한 사항을 바탕으로 다시 짠 것입니다.
> 의존성 다운로드가 막힌 환경에서 작성해 **컴파일·실행은 검증하지 않았습니다.**

## 역할
Spring Cloud Gateway(WebFlux). JWT 서명·만료만 확인하고 각 서비스로 라우팅. 역할·권한 검사는 하지 않음(각 업무 서비스·공통 라이브러리가 담당).

## 빌드
```bash
./gradlew build
```
(공통 보안 라이브러리에 의존하지 않음)

## 관련 저장소
| 저장소 | 역할 |
|---|---|
| cmj-auth-app | 전체 구조 설명 · DB 스크립트 · 화면 공통 코드 (문서용) |
| cmj-common-security | 공통 보안 라이브러리 |
| cmj-gateway | 게이트웨이 |
| cmj-auth-server | 토큰 발급 (OAuth2) |
| cmj-security-server | 로그인 규칙 |
| cmj-portal-api | 포털·관리자 API |
| cmj-hr-sync-batch | 인사 동기화 배치 |

⚠️ 공통 라이브러리의 실제 배포 방식(사내 패키지 저장소 등)은 확인되지 않아, 예시는 mavenLocal 을 씁니다.
