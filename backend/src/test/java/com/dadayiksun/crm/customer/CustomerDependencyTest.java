package com.dadayiksun.crm.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * customer는 위층 도메인을 import하지 않는다 (API 설계 4-1 도메인 방향, K13).
 * 예정 일정은 '아이 등록됨' 이벤트로만 만들어진다. customer가 cycle을 부르면 이 테스트가 깨진다.
 */
class CustomerDependencyTest {

    private static final Path CUSTOMER_SOURCES = Path.of("src/main/java/com/dadayiksun/crm/customer");
    /** customer보다 위층 (읽기 방향 common ← auth ← customer ← visit ← cycle ← contact ← target ← …, view). */
    private static final List<String> UPPER_PACKAGES = List.of("visit", "cycle", "contact", "target", "dashboard",
            "report", "search", "admin", "view");

    @Test
    void customer는_cycle을_비롯한_위층_패키지를_import하지_않는다() throws IOException {
        assertThat(CUSTOMER_SOURCES).isDirectory();
        try (Stream<Path> files = Files.walk(CUSTOMER_SOURCES)) {
            List<String> violations = files.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(CustomerDependencyTest::upperImports)
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private static Stream<String> upperImports(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(line -> UPPER_PACKAGES.stream()
                            .anyMatch(pkg -> line.contains("com.dadayiksun.crm." + pkg + ".")))
                    .map(line -> file.getFileName() + ": " + line.trim());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
