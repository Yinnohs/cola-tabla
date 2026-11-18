package com.kanbanlive.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** El primer nivel de paquetes grita el negocio, no el framework. */
class ScreamingPackagesTest {

    static final String ROOT = "com.kanbanlive";

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void importsSomething() {
        assertThat(CLASSES).isNotEmpty();
    }

    @Test
    void noFrameworkNamedPackagesAtFirstLevel() {
        noClasses().should().resideInAnyPackage(
                        ROOT + ".controller..", ROOT + ".service..", ROOT + ".repository..",
                        ROOT + ".config..", ROOT + ".util..", ROOT + ".model..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void firstLevelPackagesAreCapabilitiesOrInfrastructure() {
        classes().should().resideInAnyPackage(
                        ROOT,
                        ROOT + ".workspaces..", ROOT + ".membership..", ROOT + ".boards..", ROOT + ".lists..",
                        ROOT + ".cards..", ROOT + ".collaboration..", ROOT + ".presence..",
                        ROOT + ".sharedkernel..", ROOT + ".platform..")
                .check(CLASSES);
    }
}
