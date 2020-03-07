package com.tngtech.archunit.core.importer;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.tngtech.archunit.Slow;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.testutil.TransientCopyRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.tngtech.archunit.core.domain.SourceTest.urlOf;
import static com.tngtech.archunit.core.importer.ClassFileImporterTest.jarFileOf;
import static com.tngtech.archunit.testutil.Assertions.assertThat;
import static com.tngtech.archunit.testutil.Assertions.assertThatClasses;

@Category(Slow.class)
public class ClassFileImporterSlowTest {
    @Rule
    public final TransientCopyRule copyRule = new TransientCopyRule();

    private final ImportOption javaBase = new ImportOption() {
        @Override
        public boolean includes(Location location) {
            return location.asURI().getScheme().equals("jrt") &&
                    location.contains("java.base"); // Only import the base jdk classes
        }
    };

    @Test
    public void imports_all_classes() {
        JavaClasses classes = new ClassFileImporter().importAllClasses();

        assertThatClasses(classes).contain(ClassFileImporter.class, getClass()); // our own classes
        assertThatClasses(classes).doNotContain(Test.class, ImmutableList.class); // classes from jars
        assertThatClasses(classes).doNotContain(String.class, Object.class);// classes from java.base
    }

    @Test
    public void imports_all_classes_with_import_options() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importAllClasses();

        assertThatClasses(classes).contain(ClassFileImporter.class); // our own main class
        assertThatClasses(classes).doNotContain(getClass()); // our own test class
    }

    @Test
    public void imports_the_classpath() {
        JavaClasses classes = new ClassFileImporter().importClasspath();

        assertThatClasses(classes).contain(ClassFileImporter.class, getClass());
        assertThatClasses(classes).doNotContain(Rule.class); // Default does not import jars

        JavaClasses javaBaseClasses = importJavaBase();

        assertThatClasses(javaBaseClasses).contain(String.class, Annotation.class);
        assertThatClasses(javaBaseClasses).doNotContain(ClassFileImporter.class, getClass(), Rule.class);
    }

    @Test
    public void imports_entire_classpath() {
        JavaClasses classes = new ClassFileImporter().importEntireClasspath();

        assertThatClasses(classes).contain(ClassFileImporter.class, getClass()); // our own classes
        assertThatClasses(classes).contain(Test.class, ImmutableList.class); // classes from jars
        assertThatClasses(classes).contain(String.class, Object.class); // classes from java.base
    }

    @Test
    public void imports_entire_classpath_with_import_options() {
        JavaClasses classes = new ClassFileImporter().withImportOption(javaBase).importEntireClasspath();

        assertThatClasses(classes).doNotContain(Test.class, ImmutableList.class); // classes from jars
        assertThatClasses(classes).doNotContain(ClassFileImporter.class, getClass()); // our own classes
        assertThatClasses(classes).contain(String.class, Object.class); // classes from java.base
    }

    @Test
    public void imports_packages() {
        JavaClasses classes = new ClassFileImporter().importPackages(
                getClass().getPackage().getName(), Rule.class.getPackage().getName());
        assertThatClasses(classes).contain(ImmutableSet.of(getClass(), Rule.class));

        classes = new ClassFileImporter().importPackages(
                ImmutableSet.of(getClass().getPackage().getName(), Rule.class.getPackage().getName()));
        assertThatClasses(classes).contain(ImmutableSet.of(getClass(), Rule.class));
    }

    @Test
    public void imports_packages_of_classes() {
        JavaClasses classes = new ClassFileImporter().importPackagesOf(getClass(), Rule.class);
        assertThatClasses(classes).contain(ImmutableSet.of(getClass(), Rule.class));

        classes = new ClassFileImporter().importPackagesOf(ImmutableSet.of(getClass(), Rule.class));
        assertThatClasses(classes).contain(ImmutableSet.of(getClass(), Rule.class));
    }

    @Test
    public void imports_jars() throws Exception {
        JavaClasses classes = new ClassFileImporter().importJar(jarFileOf(Rule.class));
        assertThatClasses(classes).contain(Rule.class);
        assertThatClasses(classes).doNotContain(Object.class, ImmutableList.class);

        classes = new ClassFileImporter().importJars(jarFileOf(Rule.class), jarFileOf(ImmutableList.class));
        assertThatClasses(classes).contain(Rule.class, ImmutableList.class);
        assertThatClasses(classes).doNotContain(Object.class);

        classes = new ClassFileImporter().importJars(ImmutableList.of(
                jarFileOf(Rule.class), jarFileOf(ImmutableList.class)));
        assertThatClasses(classes).contain(Rule.class, ImmutableList.class);
        assertThatClasses(classes).doNotContain(Object.class);
    }

    @Test
    public void imports_duplicate_classes() throws IOException {
        String existingClass = urlOf(JavaClass.class).getFile();
        copyRule.copy(
                new File(existingClass),
                new File(getClass().getResource(".").getFile()));

        JavaClasses classes = new ClassFileImporter().importPackages(getClass().getPackage().getName());

        assertThat(classes.get(JavaClass.class)).isNotNull();
    }

    @Test
    public void creates_JavaPackages() {
        JavaClasses javaClasses = importJavaBase();

        JavaPackage defaultPackage = javaClasses.getDefaultPackage();

        assertThat(defaultPackage.containsPackage("java"))
                .as("Created default package contains 'java'").isTrue();

        JavaPackage javaPackage = defaultPackage.getPackage("java.lang");
        assertThatClasses(javaPackage.getClasses()).contain(Object.class, String.class, Integer.class);
        assertThatClasses(javaPackage.getAllClasses()).contain(Object.class, Annotation.class, Field.class);

        assertThat(javaClasses.containPackage("java.util"))
                .as("Classes contain package 'java.util'").isTrue();
        assertThatClasses(javaClasses.getPackage("java.util").getClasses()).contain(List.class);
    }

    private JavaClasses importJavaBase() {
        return new ClassFileImporter().importClasspath(new ImportOptions().with(javaBase));
    }
}
