package ${packageName}.service;

<#if jakartaValidation>
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
<#else>
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
</#if>
import lombok.Getter;
import lombok.Setter;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

/**
 * ${domainKr} VO
 * 테이블: ${tableName}
 * @author Claude AI
 * @since ${date}
 */
@Getter
@Setter
public class ${domain}VO {

<#list fields as f>
    // ${f.comment}
<#-- formFields에 없는 필드는 화면에서 입력받지 않으므로(컨트롤러가 서버측 기본값을 채움)
     요청 Bean Validation 대상에서 제외한다. -->
<#if !f.pk && f.required && formFields?seq_contains(f)>
<#if f.stringType>
    @NotBlank
<#else>
    @NotNull
</#if>
</#if>
<#if f.maxLength??>
    @Size(max = ${f.maxLength?c})
</#if>
    private ${f.javaType} ${f.javaName};

</#list>
<#list queryContract.displayFields() as f>
    // ${f.comment} (화면명세 JOIN/공통코드 표시 필드)
    private ${f.javaType} ${f.javaName};

</#list>
    // 페이징/검색 공통 필드
    private int pageIndex = 1;
    private int pageUnit = 10;
    private int pageSize = 10;
    private int firstIndex = 0;
    private int lastIndex = 0;
    private int recordCountPerPage = 10;
    private String searchCondition = "";
    private String searchKeyword = "";
    private PaginationInfo paginationInfo;
}
