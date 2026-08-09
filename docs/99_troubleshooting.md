# 트러블슈팅 및 자주 나오는 오류 가이드

---

## 1. PgVector 벡터 차원 불일치 (Vector Dimension Mismatch)
- **현상**: `ERROR: different vector dimensions 768 and 1536`
- **해결**: `application.yaml`의 PgVector `dimensions: 1536`과 Gemini 임베딩 모델 설정을 동일하게 일치시킵니다.

---

## 2. YAML `@cf/` 특수문자 파싱 에러
- **현상**: `ScannerException: while scanning an alias...`
- **해결**: YAML에서 `@` 문자는 따옴표(`"`)로 감싸야 합니다.  
  `model: "@cf/black-forest-labs/flux-1-schnell"`

---

## 3. Cloudflare REST API 404 URL 인코딩 이슈
- **현상**: `404 Not Found from POST .../ai/run/%40cf%2F...`
- **해결**: RestClient URI 템플릿 변수를 쓰지 않고 문자열 결합을 사용합니다.  
  `String endpoint = "/ai/run/" + modelName;`

---

## 5. Supabase Storage S3 연결 오류
- **현상**: `SdkClientException: S3 Endpoint Not Found`
- **해결**: `application.yaml`에 `path-style-access-enabled: true` 설정을 명시합니다.

---

## 6. PDF 문서 텍스트 추출 0자 문제
- **현상**: PDF 업로드 후 청크가 생성되지 않음
- **해결**: 스캔된 이미지 PDF는 2부의 **Image RAG** 멀티모달 파이프라인으로 유도합니다.
