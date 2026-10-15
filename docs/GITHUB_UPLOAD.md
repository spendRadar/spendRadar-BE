# 백엔드 저장소 업로드

로컬 Git 저장소를 main 브랜치로 초기화했습니다. 원격 저장소 연결·커밋·push는 아직 하지 않았습니다.
프로젝트는 Spring Boot와 Python Chronos 서버를 함께 포함하는 백엔드 저장소용입니다.

## 준비된 것

- `.gitignore`: 실제 키가 든 개인 설정, DB·모델·시연 결과, 가상환경, 빌드 산출물 제외.
- `application-local.properties.example`: 팀원이 복사할 빈 키 설정 예시.
- `docs/api.http`: VS Code REST Client에서 실행 가능한 요청 예시.
- `docs/AI_SETUP.md`: Python/Java 설치와 실행, 대화 계약·오류 설명.
- `docs/SYNTHETIC_DATA.md`: 3년 합성 데이터 재생성 및 별도 H2 실행.
- `.github/workflows/ci.yml`: push/PR 시 Java 테스트·빌드와 Python 계약 테스트.
- `scripts/check_repository.py`: 업로드 후보 내 키 패턴과 로컬 설정 포함 검사. 실제 키 값은 출력하지 않음.

CI는 유료 OpenAI API나 실제 Chronos 다운로드를 실행하지 않습니다.
실제 모델 정확도 평가는 별도의 백테스트로 수행합니다.
Swagger UI는 추가하지 않았으며 현재 API 계약은 README·AI_SETUP·api.http로 제공합니다.

## 1. GitHub에서 빈 저장소 만들기

spendRadar 조직에서 `spendRadar-BE`를 생성합니다. 기존 백엔드 저장소가 있으면 해당 저장소를 사용합니다.
README, .gitignore, License로 원격을 초기화하지 않습니다. 로컬 파일을 첫 커밋으로 올리기 위함입니다.
기존 파일·커밋이 있는 저장소에는 아래 첫 push 절차를 그대로 사용하지 말고 기존 이력과 통합해야 합니다.

## 2. 로컬 확인 및 커밋

프로젝트 루트의 PowerShell에서 실행합니다.

```powershell
python scripts/check_repository.py
git add .
python scripts/check_repository.py
git diff --cached --stat
git status
```

위 목록에 `application-local.properties`, `data/`, `.venv/`, `bin/`, `build/`가 없어야 합니다.
패턴 검사는 모든 비밀을 판별하지 못하므로 변경 파일도 확인합니다.
실제 개인 키는 로컬 파일에 그대로 두어도 됩니다.

```powershell
git commit -m "Initial backend with ledger CRUD and AI forecasting"
```

Git 사용자 이름/이메일을 요구하면 본인 정보로 **이 저장소에만** 설정합니다.

```powershell
git config user.name "본인 GitHub 이름"
git config user.email "본인 GitHub 이메일 또는 noreply 주소"
```

설정 후 commit 명령을 다시 실행합니다.

## 3. 원격 연결과 업로드

실제 생성한 저장소 이름이 spendRadar-BE일 때:

```powershell
git remote add origin https://github.com/spendRadar/spendRadar-BE.git
git push -u origin main
```

원격이 이미 등록되어 있으면 `git remote -v`로 대상부터 확인하세요. 강제 push는 사용하지 않습니다.
브라우저 인증이 뜨면 GitHub 계정으로 로그인합니다. GitHub 계정에 조직 저장소 쓰기 권한이 필요합니다.
GitHub 토큰이나 OpenAI 키를 원격 URL 또는 명령에 넣지 않습니다.
업로드 후 Actions 탭에서 자동 검증 결과를 확인합니다. 원격 CI는 실제 push 전에는 검증할 수 없습니다.

## 팀원에게 전달할 내용

Python 3.12와 Java 21을 준비하고 README에서 시작하면 됩니다.
data 폴더는 업로드하지 않으므로 `python scripts/generate_synthetic_data.py`로 합성 자료를 생성합니다.
프론트는 백엔드 `http://localhost:8080/api`에 연결하며 기본 허용 Origin은 localhost:5173/3000입니다.
대화 로딩 표시·오류 처리·추가 질문 상태와 구조화 예측 필드를 연결하세요.
현재는 사용자 인증과 사용자별 데이터 분리가 없는 로컬 개발·시연 버전입니다.
