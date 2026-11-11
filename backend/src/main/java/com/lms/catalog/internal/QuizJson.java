package com.lms.catalog.internal;

import com.lms.catalog.CourseCatalog.Option;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** JSON columns of {@link Question}: options [{id, text}] and the answer key [optionId…]. */
final class QuizJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private QuizJson() {
    }

    static String options(List<Option> options) {
        return JSON.writeValueAsString(options);
    }

    static List<Option> options(String json) {
        return JSON.readValue(json, new TypeReference<List<Option>>() {
        });
    }

    static String ids(List<String> ids) {
        return JSON.writeValueAsString(ids);
    }

    static List<String> ids(String json) {
        return JSON.readValue(json, new TypeReference<List<String>>() {
        });
    }
}
