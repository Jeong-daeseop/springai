# Code Generation Request

## User Request

{{userRequest}}

## Request Type

{{requestType}}

## Selected Tool

{{selectedTool}}

## Tool Selection Reason

{{toolSelectionReason}}

## Collected Context

### Project Context

{{projectContext}}

### Table Schema

{{tableSchema}}

### Table Relations

{{tableRelations}}

### RAG Context

{{ragContext}}

### Existing Code Pattern

{{existingCodePattern}}

## Generation Target

- Domain: `{{domain}}`
- Package: `{{packageName}}`
- eGovFrame Version: `{{egovVersion}}`
- Project Type: `{{projectType}}`
- Build Tool: `{{buildTool}}`
- Output Path: `{{outputPath}}`

## Generation Rules

- eGovFrame 표준 레이어 구조를 따른다.
- Controller는 Spring MVC Controller 구조를 사용한다.
- Service는 interface + ServiceImpl 구조를 사용한다.
- Repository/Mapper는 프로젝트 기준에 맞춰 MyBatis 또는 JdbcTemplate 패턴을 따른다.
- Mapper XML을 사용하는 경우 namespace, resultMap, SQL id를 Mapper interface와 일치시킨다.
- 화면 파일은 프로젝트 기준 View 기술을 따른다.
- eGovFrame 5.0 기준이면 Jakarta namespace를 사용한다.
- eGovFrame 4.3 기준이면 Javax namespace를 사용한다.
- Tool이 제공한 스키마, 경로, 버전, 패키지 값을 임의로 바꾸지 않는다.
- 생성 후 검증 가능한 파일 단위로 결과를 분리한다.

## Output Format

다음 순서로 출력한다.

1. 생성 요약
2. 사용한 Tool
3. 생성 파일 목록
4. 파일별 코드
5. 후속 검증 명령 또는 검증 Tool
6. 주의 사항

## Required Output Files

{{requiredOutputFiles}}

## Validation Checklist

- [ ] 패키지 경로가 `{{packageName}}`와 일치한다.
- [ ] 테이블명과 컬럼명이 `{{tableSchema}}` 기준과 일치한다.
- [ ] PK 필드가 누락되지 않았다.
- [ ] Controller URL prefix가 `{{urlPrefix}}`와 일치한다.
- [ ] Service interface와 ServiceImpl 메서드 시그니처가 일치한다.
- [ ] Mapper interface와 Mapper XML id가 일치한다.
- [ ] JSP/HTML 화면의 form field name이 VO 필드명과 일치한다.
- [ ] eGovFrame 버전에 맞는 import를 사용한다.
- [ ] 생성 후 `validateGeneratedCodeDirectory()`로 검증 가능하다.

## Error Handling

- 필수 컨텍스트가 비어 있으면 코드를 생성하지 말고 누락 항목을 명시한다.
- Tool 결과가 오류 문자열이면 해당 오류를 우선 해결한다.
- DB 변경 SQL은 실행하지 말고 검토용 SQL로만 출력한다.
- 출력 경로가 확정되지 않았으면 파일 저장을 진행하지 않는다.
