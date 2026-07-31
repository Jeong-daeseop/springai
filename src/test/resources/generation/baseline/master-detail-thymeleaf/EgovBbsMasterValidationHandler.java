package egovframework.let.bbs.web;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * BBSMASTER Validation 전역 예외 핸들러
 * @author Claude AI
 * @since GENERATED_DATE
 */
@ControllerAdvice(assignableTypes = EgovBbsMasterController.class)
public class EgovBbsMasterValidationHandler {

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
