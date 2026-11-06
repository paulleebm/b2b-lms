package com.lms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Fails the build if a module reaches into another module's internals or modules form a cycle. */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(LmsApplication.class);

    @Test
    @DisplayName("모듈 경계 위반(내부 패키지 참조, 순환 의존)이 없다")
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    @DisplayName("모듈 구조 문서(PlantUML)를 생성한다")
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
