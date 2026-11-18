package com.kanbanlive.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Reglas 4.2 del plan: domain, application, adapter; entre capacidades solo via port.in o eventos. */
class HexagonalArchitectureTest {

    static final String ROOT = "com.kanbanlive";
    static final List<String> CAPABILITIES =
            List.of("workspaces", "membership", "boards", "lists", "cards", "collaboration", "presence");

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    // allowEmptyShould: el esqueleto de Fase 0 aun no tiene clases en muchas capacidades.
    private static void check(ArchRule rule) {
        rule.allowEmptyShould(true).check(CLASSES);
    }

    @Test
    void domainIsFrameworkFreeAndInnermost() {
        check(noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.validation..",
                        "com.fasterxml.jackson..", "..application..", "..adapter..", "..platform.."));
    }

    @Test
    void sharedKernelDoesNotUseSpring() {
        check(noClasses().that().resideInAPackage(ROOT + ".sharedkernel..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..",
                        ROOT + ".platform..", "..adapter.."));
    }

    @Test
    void applicationDoesNotDependOnAdaptersOrWebOrJpa() {
        check(noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter..", "..platform..", "org.springframework.web..",
                        "org.springframework.data..", "jakarta.persistence..", "org.hibernate.."));
    }

    @Test
    void inboundAdaptersDoNotDependOnOutboundAdapters() {
        check(noClasses().that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out.."));
    }

    @Test
    void outboundAdaptersDoNotDependOnInboundAdapters() {
        check(noClasses().that().resideInAPackage("..adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.in.."));
    }

    @Test
    void inboundAdaptersOnlyUseInPortsNotOutPortsOrServices() {
        check(noClasses().that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.port.out..", "..application.service.."));
    }

    @Test
    void nobodyDependsOnPlatformExceptTheBootstrap() {
        check(noClasses()
                .that().resideOutsideOfPackages(ROOT + ".platform..")
                .and().doNotHaveFullyQualifiedName(ROOT + ".KanbanLiveApplication")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".platform.."));
    }

    @TestFactory
    List<DynamicTest> capabilitiesOnlyTalkThroughPortInOrEvents() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String a : CAPABILITIES) {
            for (String b : CAPABILITIES) {
                if (a.equals(b)) {
                    continue;
                }
                tests.add(DynamicTest.dynamicTest(a + " -> " + b, () -> check(
                        noClasses().that().resideInAPackage(ROOT + "." + a + "..")
                                .should().dependOnClassesThat().resideInAnyPackage(
                                        ROOT + "." + b + ".domain.model..",
                                        ROOT + "." + b + ".domain.error..",
                                        ROOT + "." + b + ".application.port.out..",
                                        ROOT + "." + b + ".application.service..",
                                        ROOT + "." + b + ".adapter.."))));
            }
        }
        return tests;
    }
}
