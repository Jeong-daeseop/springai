package egovframework.let.bbs.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * BBS Validation Handler
 */
@ControllerAdvice(assignableTypes = EgovBbsController.class)
public class EgovBbsValidationHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public String handleValidation(ConstraintViolationException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return "bbs/EgovBbsList";
    }
}
