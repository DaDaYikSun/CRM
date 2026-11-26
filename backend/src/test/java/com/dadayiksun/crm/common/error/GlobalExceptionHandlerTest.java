package com.dadayiksun.crm.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** 오류는 공통 응답 한 곳에서, 코드 · 문구는 API 설계 1-5 · 정책 4-2와 같게 (K16 ②). */
class GlobalExceptionHandlerTest {

    record PetForm(@NotBlank(message = "아이 이름") String name) {
    }

    @RestController
    static class SampleController {

        @PostMapping("/api/sample")
        ApiResponse<String> create(@Valid @RequestBody PetForm form) {
            return ApiResponse.of(form.name());
        }

        @GetMapping("/api/sample")
        ApiResponse<PageResponse<String>> list(@RequestParam(required = false) String page) {
            PageQuery q = PageQuery.parse(page);
            long total = 3;
            List<String> items = q.isBeyond(total) ? List.of() : List.of("a", "b", "c");
            return ApiResponse.of(PageResponse.of(items, q, total));
        }

        @GetMapping("/api/sample/missing")
        ApiResponse<String> missing() {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        @GetMapping("/api/sample/boom")
        ApiResponse<String> boom() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/api/sample/boom")
        ApiResponse<String> boomSave() {
            throw new IllegalStateException("boom");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 필수_칸이_비면_항목_이름으로_문구를_만든다() throws Exception {
        mvc.perform(post("/api/sample").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("아이 이름을(를) 입력해 주세요."))
                .andExpect(jsonPath("$.error.fields[0].field").value("name"))
                .andExpect(jsonPath("$.error.fields[0].message").value("아이 이름을(를) 입력해 주세요."));
    }

    @Test
    void page가_0이나_문자면_400() throws Exception {
        mvc.perform(get("/api/sample").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/sample").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 마지막보다_큰_page는_빈_items와_total() throws Exception {
        mvc.perform(get("/api/sample").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void 업무_오류는_정책_문구_그대로() throws Exception {
        mvc.perform(get("/api/sample/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("찾는 정보가 없습니다. 목록에서 다시 찾아 주세요."))
                .andExpect(jsonPath("$.error.fields").doesNotExist());
    }

    @Test
    void 서버_오류는_조회와_저장_문구가_다르다() throws Exception {
        mvc.perform(get("/api/sample/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("불러오지 못했습니다. 다시 시도해 주세요."));
        mvc.perform(post("/api/sample/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.message").value("저장하지 못했습니다. 다시 시도해 주세요."));
    }
}
