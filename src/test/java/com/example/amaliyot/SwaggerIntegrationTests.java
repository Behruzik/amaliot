package com.example.amaliyot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class SwaggerIntegrationTests {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousUsersCanOpenSwaggerUiAndItsConfiguration() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("swagger-ui/index.html")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @Test
    void apiDocumentIncludesBearerLoginExamplesAndPrivatePdfContracts() throws Exception {
        mvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.info.description", containsString("без слова Bearer")))
                .andExpect(jsonPath("$['paths']['/api/auth/login']['post']['requestBody']['content']['application/json']['examples']['demoUser']['value']['email']")
                        .value("student.demo@gmail.com"))
                .andExpect(jsonPath("$['paths']['/api/auth/login']['post']['requestBody']['content']['application/json']['examples']['demoAdmin']['value']['email']")
                        .value("admin.demo@example.test"))
                .andExpect(jsonPath("$['paths']['/api/profile']['get']['security'][0]['bearerAuth']").isArray())
                .andExpect(jsonPath("$['paths']['/api/tests/results/{attemptId}/pdf']['get']['security'][0]['bearerAuth']").isArray())
                .andExpect(jsonPath("$['paths']['/api/tests/results/{attemptId}/pdf']['get']['responses']['200']['content']['application/pdf']['schema']['format']")
                        .value("binary"))
                .andExpect(jsonPath("$['paths']['/api/tests/results/{attemptId}/pdf']['get']['responses']['409']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/ApiError"))
                .andExpect(jsonPath("$['paths']['/api/certificates/{certificateNumber}/pdf']['get']['responses']['200']['content']['application/pdf']").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.timestamp").exists());
    }
}
