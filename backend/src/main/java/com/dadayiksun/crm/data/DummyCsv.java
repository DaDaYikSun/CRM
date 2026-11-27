package com.dadayiksun.crm.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;

/**
 * 더미 CSV 읽기 (backend/src/main/resources/db/dummy, 생성 스크립트가 만든 파일).
 * 큰따옴표로 감싼 칸(쉼표 · 줄바꿈 · "" 포함)을 지원한다. 빈 칸은 빈 문자열이다.
 */
public final class DummyCsv {

    private DummyCsv() {
    }

    public static List<Map<String, String>> read(String fileName) {
        ClassPathResource resource = new ClassPathResource("db/dummy/" + fileName);
        try (InputStream in = resource.getInputStream()) {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("더미 CSV를 읽지 못했다: " + fileName, e);
        }
    }

    public static List<Map<String, String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = rows.get(0);
        List<Map<String, String>> out = new ArrayList<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            if (r.size() == 1 && r.get(0).isEmpty()) {
                continue; // 빈 줄
            }
            if (r.size() != header.size()) {
                throw new IllegalStateException("CSV 칸 수가 머리줄과 다르다: " + r);
            }
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                m.put(header.get(i), r.get(i));
            }
            out.add(m);
        }
        return out;
    }
}
