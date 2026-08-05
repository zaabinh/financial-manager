package com.example.financemanager.shared.error;

import com.example.financemanager.shared.api.ApiResponse;
import com.example.financemanager.shared.logging.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiFoundationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FoundationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsSuccessEnvelopeAndGeneratedCorrelationId() throws Exception {
        mockMvc.perform(post("/api/v1/foundation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"ready\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        CorrelationIdFilter.HEADER_NAME,
                        matchesPattern("^[0-9a-f-]{36}$")
                ))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ready"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returnsFieldErrorsForValidJsonThatViolatesConstraints() throws Exception {
        mockMvc.perform(post("/api/v1/foundation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("value"));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/foundation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void propagatesSafeCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/foundation/rule")
                        .header(CorrelationIdFilter.HEADER_NAME, "mobile-request-123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "mobile-request-123"))
                .andExpect(jsonPath("$.correlationId").value("mobile-request-123"));
    }

    @Test
    void replacesUnsafeCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/foundation/rule")
                        .header(CorrelationIdFilter.HEADER_NAME, "unsafe id with spaces"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string(
                        CorrelationIdFilter.HEADER_NAME,
                        matchesPattern("^[0-9a-f-]{36}$")
                ));
    }

    @RestController
    @RequestMapping("/api/v1/foundation")
    static class FoundationController {

        @PostMapping
        ApiResponse<String> validate(@Valid @RequestBody FoundationRequest request) {
            return ApiResponse.success(request.value(), "Foundation ready");
        }

        @GetMapping("/rule")
        void businessRuleFailure() {
            throw new UnprocessableEntityException("FOUNDATION_RULE", "Foundation rule failed");
        }
    }

    record FoundationRequest(@NotBlank String value) {
    }
}
