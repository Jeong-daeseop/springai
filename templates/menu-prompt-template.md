# Menu Generation Request

## User Request

{{userRequest}}

## Selected Tool Flow

1. `MenuTool.getMenuStructure("{{upperMenuNo}}")`
2. `AuthTool.getProgramList("{{programKeyword}}")`
3. `MenuTool.generateMenuInsertSql(...)`
4. 필요한 경우 `AuthTool.generateAuthInsertSql(...)`

## Menu Target

| Key | Value |
| --- | --- |
| Upper Menu No | `{{upperMenuNo}}` |
| Menu Name | `{{menuNm}}` |
| URL Prefix | `{{urlPrefix}}` |
| Program File Name | `{{progrmFileNm}}` |
| Domain | `{{domain}}` |
| Program Korean Name | `{{programNm}}` |

## Menu Structure Result

{{menuStructure}}

## Program Duplicate Check Result

{{programListResult}}

## Generation Rules

- 먼저 `getMenuStructure()`로 상위 메뉴 존재 여부와 신규 `MENU_NO`, `MENU_ORDR` 권장값을 확인한다.
- `menuNo`는 숫자 문자열이어야 한다.
- `getProgramList()`로 `PROGRM_FILE_NM` 중복 여부를 확인한다.
- `generateMenuInsertSql()`은 SQL을 반환만 한다.
- 반환된 SQL은 사용자가 검토 후 직접 실행해야 한다.
- URL 권한 등록까지 필요하면 `generateAuthInsertSql()`을 별도로 호출한다.
- DB 방언은 서버 설정 `app.sql.dialect`를 따른다.

## Required SQL

### Program SQL

```sql
{{programInsertSql}}
```

### Menu SQL

```sql
{{menuInsertSql}}
```

### Optional Auth SQL

```sql
{{authInsertSql}}
```

## Output Format

1. 상위 메뉴 확인 결과
2. 신규 메뉴 권장값
3. 프로그램 중복 확인 결과
4. 생성 SQL
5. 실행 전 확인 사항
6. 실행 후 조치

## Validation Checklist

- [ ] 상위 메뉴가 존재한다.
- [ ] 신규 `MENU_NO`가 기존 하위 메뉴와 충돌하지 않는다.
- [ ] 신규 `MENU_ORDR`가 기존 하위 메뉴 다음 순서다.
- [ ] `PROGRM_FILE_NM`이 중복되지 않는다.
- [ ] URL Prefix가 기존 프로그램 URL과 중복되지 않는다.
- [ ] Security가 DB 기반 URL 권한을 참조하는 구조다.

## Stop Conditions

- 상위 메뉴가 존재하지 않으면 SQL을 생성하지 않는다.
- `PROGRM_FILE_NM`이 중복되면 SQL을 생성하지 않는다.
- `urlPrefix`, `menuNm`, `progrmFileNm` 중 하나라도 비어 있으면 SQL을 생성하지 않는다.
- SQL 실행은 수행하지 않는다.
