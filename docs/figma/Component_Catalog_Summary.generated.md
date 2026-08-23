<!-- GENERATED FILE. Do not edit manually. Source: website-figma-contract/component-catalog-v2.json -->

# Component Catalog v2 요약

| logicalType | kind | requirement | properties | composition |
|---|---|---|---|---|
| egov.actionArea | PATTERN | REQUIRED | - | krds.button |
| egov.dataTable | PATTERN | REQUIRED | - | krds.tableHeader, krds.tableCell |
| egov.detailPage | PAGE_TEMPLATE | OPTIONAL | - | egov.pattern.detail |
| egov.formPage | PAGE_TEMPLATE | REQUIRED | - | egov.pattern.form |
| egov.formSection | PATTERN | REQUIRED | - | krds.textField |
| egov.listPage | PAGE_TEMPLATE | REQUIRED | - | egov.pattern.list |
| egov.pageHeader | PATTERN | REQUIRED | - | krds.pageHeader |
| egov.pattern.detail | PATTERN | OPTIONAL | - | egov.pageHeader, egov.formSection, egov.actionArea |
| egov.pattern.form | PATTERN | REQUIRED | - | egov.pageHeader, egov.formSection, egov.actionArea |
| egov.pattern.list | PATTERN | REQUIRED | - | egov.pageHeader, krds.searchPanel, egov.dataTable, krds.pagination |
| egov.pattern.masterDetail | PATTERN | OPTIONAL | - | egov.pattern.list, egov.pattern.detail |
| krds.button | COMPONENT | REQUIRED | label (TEXT), variant (VARIANT), disabled (BOOLEAN) | - |
| krds.card | COMPONENT | REQUIRED | - | - |
| krds.cardList | PATTERN | OPTIONAL | - | krds.container, krds.card |
| krds.checkbox | COMPONENT | REQUIRED | label (TEXT), checked (BOOLEAN) | - |
| krds.container | COMPONENT | REQUIRED | - | - |
| krds.dataTable | PATTERN | REQUIRED | - | krds.tableHeader, krds.tableCell |
| krds.datePicker | COMPONENT | OPTIONAL | - | - |
| krds.pageHeader | COMPONENT | REQUIRED | title (TEXT) | - |
| krds.pagination | COMPONENT | REQUIRED | currentPage (TEXT) | - |
| krds.radio | COMPONENT | OPTIONAL | - | - |
| krds.searchPanel | COMPONENT | REQUIRED | type (VARIANT), size (VARIANT), state (VARIANT) | - |
| krds.select | COMPONENT | REQUIRED | label (TEXT), value (TEXT) | - |
| krds.tableCell | COMPONENT | REQUIRED | - | - |
| krds.tableHeader | COMPONENT | REQUIRED | - | - |
| krds.textField | COMPONENT | REQUIRED | label (TEXT), required (BOOLEAN), value (TEXT) | - |
| krds.textarea | COMPONENT | OPTIONAL | label (TEXT), value (TEXT) | - |
