package ${packageName}.web;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ${domainKr} Validation 전역 예외 핸들러
 * BindingResult 없이 @Valid 검증 실패 시 HTTP 400 대신 에러 메시지와 함께 리다이렉트합니다.
 * @author Claude AI
 * @since ${date}
 */
@ControllerAdvice(assignableTypes = Egov${domain}Controller.class)
public class Egov${domain}ValidationHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleValidationError(
            MethodArgumentNotValidException ex,
            RedirectAttributes redirectAttributes) {

        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .findFirst()
            .orElse("입력값을 확인하세요.");
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:/error";
    }
}
