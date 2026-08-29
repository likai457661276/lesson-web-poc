package com.lessonweb.lesson.contract;

import com.lessonweb.lesson.LessonApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LessonApplication.class)
@AutoConfigureMockMvc
class FormulaContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void formulaResponseUsesCamelCaseAndNullableFields() throws Exception {
        mockMvc.perform(post("/api/formulas/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latex\":\"x^2 + 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latex").value("x^2 + 1"))
                .andExpect(jsonPath("$.normalizedLatex").value("x^2 + 1"))
                .andExpect(jsonPath("$.parseable").value(true))
                .andExpect(jsonPath("$.symbolicExpression").isString())
                .andExpect(jsonPath("$.equivalentToReference").isEmpty())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void referenceLatexUsesExistingFrontendFieldName() throws Exception {
        mockMvc.perform(post("/api/formulas/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latex\":\"x\",\"referenceLatex\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equivalentToReference").value(true));
    }

    @Test
    void emptyLatexReturns422() throws Exception {
        mockMvc.perform(post("/api/formulas/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latex\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].loc[1]").value("latex"));
    }

    @Test
    void missingLatexReturns422() throws Exception {
        mockMvc.perform(post("/api/formulas/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].loc[1]").value("latex"));
    }
}
