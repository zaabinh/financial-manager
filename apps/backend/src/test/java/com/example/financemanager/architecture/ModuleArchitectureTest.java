package com.example.financemanager.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.example.financemanager");

    @Test
    void domainDoesNotDependOnApiOrInfrastructure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..infrastructure..")
                .check(classes);
    }

    @Test
    void controllersDoNotDependOnRepositories() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .check(classes);
    }

    @Test
    void sharedDoesNotDependOnFeaturePackages() {
        noClasses()
                .that().resideInAPackage("..shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..auth..",
                        "..user..",
                        "..account..",
                        "..category..",
                        "..transaction..",
                        "..budget..",
                        "..dashboard..",
                        "..analytics..",
                        "..notification..",
                        "..subscription.."
                )
                .check(classes);
    }
}
