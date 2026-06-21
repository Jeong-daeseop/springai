package ${packageName}.web;

<#if jakartaValidation>
import jakarta.validation.ConstraintViolationException;
<#else>
import javax.validation.ConstraintViolationException;
</#if>
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * ${domainKr} Validation Handler
 */
@ControllerAdvice(assignableTypes = Egov${domain}Controller.class)
public class Egov${domain}ValidationHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public String handleValidation(ConstraintViolationException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return "${domainLc}/Egov${domain}List";
    }
}
