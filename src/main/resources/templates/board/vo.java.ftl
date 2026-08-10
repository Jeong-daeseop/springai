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
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * ${domainKr} VO
 * @author Claude AI
 * @since ${date}
 */
@Getter
@Setter
public class ${domain}VO extends ${domain}SearchVO {

    private static final long serialVersionUID = 1L;

<#list fields as f>
    /** ${f.comment} */
<#-- nttId는 Service의 ID 생성기가 등록 시 채우므로, formFields에 없는 필드는 화면에서 입력받지
     않으므로(컨트롤러가 서버측 기본값을 채움) 요청 Bean Validation 대상에서 제외한다. -->
<#if f.required && f.javaName != nttId.javaName && formFields?seq_contains(f) && f.stringType>
    @NotBlank
<#elseif f.required && f.javaName != nttId.javaName && formFields?seq_contains(f) && !f.stringType>
    @NotNull
</#if>
<#if f.maxLength??>
    @Size(max = ${f.maxLength?c})
</#if>
    private ${f.javaType} ${f.javaName};

</#list>
<#list queryContract.displayFields() as f>
    /** ${f.comment} (화면명세 JOIN/공통코드 표시 필드) */
    private ${f.javaType} ${f.javaName};

</#list>
    /** 게시판명 (LETTNBBSMASTER.BBS_NM 조인 표시용) */
    private String bbsNm;

}
