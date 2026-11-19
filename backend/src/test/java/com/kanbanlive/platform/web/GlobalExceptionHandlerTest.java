package com.kanbanlive.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kanbanlive.collaboration.domain.error.EventsGoneException;
import com.kanbanlive.collaboration.domain.error.InvalidEventCursorException;
import com.kanbanlive.sharedkernel.domain.ConflictException;
import com.kanbanlive.sharedkernel.domain.ForbiddenException;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Standalone MockMvc (sin contexto Spring ni Docker). */
class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void notFoundIs404() throws Exception {
        mvc.perform(get("/t/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("card 1 not found"));
    }

    @Test
    void staleVersionIs409WithStableCode() throws Exception {
        mvc.perform(get("/t/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void staleVersionCarriesTheCurrentStateInTheBody() throws Exception {
        mvc.perform(get("/t/stale-current"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.current.id").value("c1"))
                .andExpect(jsonPath("$.current.version").value(3));
    }

    @Test
    void staleVersionWithoutStateHasNoCurrentProperty() throws Exception {
        mvc.perform(get("/t/stale"))
                .andExpect(jsonPath("$.current").doesNotExist());
    }

    @Test
    void badRequestDomainErrorIs400WithItsOwnCode() throws Exception {
        mvc.perform(get("/t/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SINCE"));
    }

    @Test
    void goneDomainErrorIs410WithItsOwnCode() throws Exception {
        mvc.perform(get("/t/gone"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EVENTS_GONE"));
    }

    @Test
    void genericConflictIs409() throws Exception {
        mvc.perform(get("/t/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void forbiddenIs403() throws Exception {
        mvc.perform(get("/t/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void domainValidationIs422() throws Exception {
        mvc.perform(get("/t/validation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void beanValidationIs422WithFieldErrors() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void malformedJsonIs400ProblemDetailWithCode() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unexpectedIs500WithoutLeakingDetails() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }

    record Body(@NotBlank String name) {
    }

    record Current(String id, long version) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/t/not-found")
        void notFound() {
            throw new NotFoundException("card 1 not found");
        }

        @GetMapping("/t/stale")
        void stale() {
            throw new StaleVersionException("stale");
        }

        @GetMapping("/t/stale-current")
        void staleWithCurrent() {
            throw new StaleVersionException("stale", new Current("c1", 3));
        }

        @GetMapping("/t/bad-request")
        void badRequest() {
            throw new InvalidEventCursorException("since ahead");
        }

        @GetMapping("/t/gone")
        void gone() {
            throw new EventsGoneException("reload");
        }

        @GetMapping("/t/conflict")
        void conflict() {
            throw new ConflictException("conflict");
        }

        @GetMapping("/t/forbidden")
        void forbidden() {
            throw new ForbiddenException("no");
        }

        @GetMapping("/t/validation")
        void validation() {
            throw new ValidationException("bad");
        }

        @GetMapping("/t/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }

        @PostMapping("/t/body")
        void body(@Valid @RequestBody Body body) {
        }
    }
}
