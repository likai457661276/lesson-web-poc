package com.lessonweb.lesson.controller;

import com.lessonweb.lesson.model.formula.FormulaValidationRequest;
import com.lessonweb.lesson.model.formula.FormulaValidationResult;
import com.lessonweb.lesson.service.FormulaValidationService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/formulas")
public class FormulaController {

    private final FormulaValidationService service;

    public FormulaController(FormulaValidationService service) {
        this.service = service;
    }

    @PostMapping("/validate")
    public FormulaValidationResult validate(@Valid @RequestBody FormulaValidationRequest request) {
        return service.validate(request);
    }
}
