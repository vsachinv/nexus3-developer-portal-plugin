package com.rxlogix.nexus.devportal.provider;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MavenPackageProviderTest {

    private static Map<String, String> parse(String pom) throws Exception {
        return MavenPackageProvider.parsePomDependencies(
                new ByteArrayInputStream(pom.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesDirectDependenciesWithCoordinates() throws Exception {
        Map<String, String> deps = parse("""
            <project>
              <groupId>com.example</groupId>
              <artifactId>demo</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>org.apache.commons</groupId>
                  <artifactId>commons-lang3</artifactId>
                  <version>3.12.0</version>
                </dependency>
                <dependency>
                  <groupId>com.google.guava</groupId>
                  <artifactId>guava</artifactId>
                  <version>33.0.0-jre</version>
                </dependency>
              </dependencies>
            </project>
            """);

        assertThat(deps)
                .containsEntry("org.apache.commons:commons-lang3", "3.12.0")
                .containsEntry("com.google.guava:guava", "33.0.0-jre")
                .hasSize(2);
    }

    @Test
    void resolvesPropertyPlaceholdersFromPomProperties() throws Exception {
        Map<String, String> deps = parse("""
            <project>
              <properties>
                <junit.version>5.10.0</junit.version>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter</artifactId>
                  <version>${junit.version}</version>
                </dependency>
              </dependencies>
            </project>
            """);

        assertThat(deps).containsEntry("org.junit.jupiter:junit-jupiter", "5.10.0");
    }

    @Test
    void ignoresDependencyManagementAndMarksManagedVersions() throws Exception {
        Map<String, String> deps = parse("""
            <project>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>org.managed</groupId>
                    <artifactId>managed-bom</artifactId>
                    <version>1.0.0</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>org.managed</groupId>
                  <artifactId>managed-lib</artifactId>
                </dependency>
              </dependencies>
            </project>
            """);

        // Only the top-level <dependencies> is read; the managed one is excluded.
        assertThat(deps).hasSize(1)
                .containsEntry("org.managed:managed-lib", "(managed)");
    }

    @Test
    void extractsProjectUrlPreferringUrlThenScm() throws Exception {
        var withUrl = MavenPackageProvider.parsePom(new ByteArrayInputStream("""
            <project>
              <url>https://example.com/demo</url>
              <scm><url>https://github.com/x/demo</url></scm>
              <issueManagement><url>https://github.com/x/demo/issues</url></issueManagement>
            </project>
            """.getBytes(StandardCharsets.UTF_8)));
        assertThat(withUrl.projectUrl).isEqualTo("https://example.com/demo");
        assertThat(withUrl.issueUrl).isEqualTo("https://github.com/x/demo/issues");

        var scmOnly = MavenPackageProvider.parsePom(new ByteArrayInputStream("""
            <project>
              <scm><url>https://github.com/x/demo</url></scm>
            </project>
            """.getBytes(StandardCharsets.UTF_8)));
        assertThat(scmOnly.projectUrl).isEqualTo("https://github.com/x/demo");

        var none = MavenPackageProvider.parsePom(new ByteArrayInputStream(
            "<project></project>".getBytes(StandardCharsets.UTF_8)));
        assertThat(none.projectUrl).isNull();
    }

    @Test
    void rejectsExternalEntities() {
        // A DOCTYPE with an external entity must not be expanded (XXE protection).
        String xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE project [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <project><dependencies>
              <dependency><groupId>&xxe;</groupId><artifactId>a</artifactId><version>1</version></dependency>
            </dependencies></project>
            """;
        // disallow-doctype-decl causes a parse exception rather than entity expansion.
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> parse(xxe));
    }
}
