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
<#if f.required && f.stringType>
    @NotBlank
<#elseif f.required && !f.stringType>
    @NotNull
</#if>
<#if f.maxLength??>
    @Size(max = ${f.maxLength})
</#if>
    private ${f.javaType} ${f.javaName};

</#list>
}
