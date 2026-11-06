package com.lms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lms.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Writes the API contract the frontend's types are generated from
 * ({@code frontend/packages/api-client/openapi.json}). The file is committed, so an API change
 * shows up in review as a contract diff, and CI can fail when it was not regenerated.
 */
class OpenApiContractIT extends IntegrationTest {

    static final Path CONTRACT = Path.of("../frontend/packages/api-client/openapi.json");

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("OpenAPI 계약 파일을 내보낸다 (프론트엔드 타입 생성의 원본)")
    void exportContract() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        Object tree = mapper.readValue(json, Object.class);
        // Servers differ per machine (random test port); the client sets its own base URL.
        if (tree instanceof java.util.Map<?, ?> map) {
            map.remove("servers");
        }
        Files.writeString(CONTRACT, mapper.writeValueAsString(tree) + "\n", StandardCharsets.UTF_8);
    }
}
