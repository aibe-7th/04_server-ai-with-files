# 4시간 실습 가이드 — PDF RAG · Image RAG · 이미지 생성

본 실습 과정은 Spring Boot 4.1.0과 Spring AI 2.0.0을 활용하여 PDF 문서 RAG, 멀티모달 기반 Image RAG, 그리고 Cloudflare FLUX 모델 기반 이미지 생성 파이프라인을 구축하는 4시간 실습 코스입니다.

---

## 🗂️ 부별 실습 순서 및 안내

### 0부. 환경 세팅과 공통 골격 (20분)
- 📄 **가이드 문서**: [`docs/01_setup.md`](file:///Users/morgan/IdeaProjects/rag-and-image/docs/01_setup.md)
- **실습 순서**:
  1. Gradle 의존성 추가 (`spring-cloud-aws-starter-s3`, `thumbnailator`)
  2. `application.yaml` 설정 작성 (Supabase S3/PgVector/Google GenAI)
  3. `@ConfigurationProperties` 바인딩 Record 클래스 작성 (`AiProperties`, `StorageProperties`)
  4. S3 연동 공통 서비스 및 클린 RESTful URL 프록시 다운로드 컨트롤러 구축 (`ObjectStorageService`, `MediaDownloadController`)
  5. 전역 예외 처리기 및 메인 홈 화면 작성 (`GlobalExceptionHandler`, `HomeController`, `home.html`)

---

### 1부. PDF RAG (80분)
- 📄 **가이드 문서**: [`docs/02_pdf_rag.md`](file:///Users/morgan/IdeaProjects/rag-and-image/docs/02_pdf_rag.md)
- **실습 순서**:
  1. PDF 문서 검증기 구현 (`PdfValidator`) — PDF Header 시그니처 및 텍스트 extraction 최소 길이 검증
  2. PDF 파싱 및 청크 분할 수집 서비스 구현 (`PdfIngestService`) — `PagePdfDocumentReader`, `TokenTextSplitter` 활용 및 메타데이터 보강 후 `vector_store` 저장
  3. 유사도 검색 및 RAG 답변 서비스 구현 (`RagQueryService`) — `similaritySearch` 저장 검증 및 `QuestionAnswerAdvisor` 기반 근거 답변 생성
  4. Web Controller 및 Thymeleaf 뷰 연동 (`RagDocumentController`, `templates/rag/index.html`)

---

### 2부. Image RAG (70분)
- 📄 **가이드 문서**: [`docs/03_image_rag.md`](file:///Users/morgan/IdeaProjects/rag-and-image/docs/03_image_rag.md)
- **실습 순서**:
  1. 이미지 검증기 및 Thumbnailator 기반 리사이저 구현 (`ImageValidator`, `ImageResizer`)
  2. Gemini Vision 멀티모달 정보 구조화 추출 서비스 구현 (`ImageInterpretService`, `ImageInterpretation`) — OCR 텍스트, 캡션, 태그 추출
  3. 이미지 전용 VectorStore 및 Data URI 변환 임베딩 어댑터 구성 (`ImageRagConfig`, `ImageDocumentEmbeddingModel`)
  4. 이미지 수집 및 S3 롤백 보상 트랜잭션 수집 서비스 구현 (`ImageIngestService`)
  5. `ownerId` 소유자 메타데이터 필터 기반 크로스모달 이미지 검색 및 클린 URL 렌더링 구현 (`ImageSearchService`, `ImageRagController`, `templates/image-rag/index.html`)

---

### 3부. 이미지 생성 (60분)
- 📄 **가이드 문서**: [`docs/04_image_generation.md`](file:///Users/morgan/IdeaProjects/rag-and-image/docs/04_image_generation.md)
- **실습 순서**:
  1. Cloudflare Workers AI FLUX 모델 DTO 및 RestClient 설정 (`CloudflareImageDtos`, `WorkersAiConfig`)
  2. 커스텀 `CloudflareImageModel` 및 `CloudflareImageOptions` 구현
  3. LLM 기반 프롬프트 자동 보강기 구현 (`PromptEnhancer`)
  4. 생성 이미지 메타데이터 영속화 JPA 엔티티/리포지토리 구현 (`GeneratedImage`, `GeneratedImageRepository`)
  5. 프롬프트 보강 → FLUX 이미지 생성 → 미리보기 → 선택 저장 서비스 구현 (`ImageGenerationService`, `ImageGenerationController`, `templates/images/form.html`)

---

## 🛠️ 트러블슈팅 가이드
- 📄 **가이드 문서**: [`docs/99_troubleshooting.md`](file:///Users/morgan/IdeaProjects/rag-and-image/docs/99_troubleshooting.md)
- PgVector 1536차원 설정, YAML 특수문자 따옴표, Cloudflare REST URL 인코딩 등 실습 중 발생 가능한 주요 이슈 정리
