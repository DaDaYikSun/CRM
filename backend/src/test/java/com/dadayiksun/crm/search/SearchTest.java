package com.dadayiksun.crm.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.dadayiksun.crm.auth.access.AuthTestSupport;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.GlobalExceptionHandler;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.search.controller.SearchController;
import com.dadayiksun.crm.search.dto.SearchItem;
import com.dadayiksun.crm.search.repository.SearchRepository;
import com.dadayiksun.crm.search.service.SearchQuery;
import com.dadayiksun.crm.search.service.SearchService;

/** FR-SRCH-001 · 002, K16 ⑥. SQL 자체는 MySQL이 필요해 여기서 확인하지 않는다. */
class SearchTest {

    private final SearchRepository repository = mock(SearchRepository.class);
    private final SearchService service = new SearchService(repository);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SearchController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void 하이픈과_띄어쓰기를_지워_같은_검색어가_된다() {
        assertThat(SearchQuery.parse("010-1234")).isEqualTo(SearchQuery.parse("0101234"));
        assertThat(SearchQuery.parse(" 010 1234 ").text()).isEqualTo("0101234");
    }

    @Test
    void 숫자만_남으면_전화번호_아니면_이름() {
        assertThat(SearchQuery.parse("0000").phone()).isTrue();
        assertThat(SearchQuery.parse("보 리").phone()).isFalse();
        assertThat(SearchQuery.parse("보리2").phone()).isFalse();
    }

    @Test
    void 빈_검색어와_기호만_있는_검색어() {
        assertThat(SearchQuery.parse(null).isEmpty()).isTrue();
        assertThat(SearchQuery.parse(" - ").isEmpty()).isTrue();
        assertThat(service.preview("-")).isEmpty();
        assertThat(service.search("", PageQuery.parse(null)).items()).isEmpty();
    }

    @Test
    void 미리보기는_5건까지() {
        service.preview("보리");
        verify(repository).find(SearchQuery.parse("보리"), 5, 0);
    }

    @Test
    void 마지막_페이지보다_크면_빈_items와_total() {
        when(repository.count(SearchQuery.parse("보리"))).thenReturn(21L);
        var page = service.search("보리", PageQuery.parse("3"));
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(21);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void 두번째_페이지는_offset_20() {
        when(repository.count(SearchQuery.parse("보리"))).thenReturn(21L);
        service.search("보리", PageQuery.parse("2"));
        verify(repository).find(SearchQuery.parse("보리"), 20, 20);
    }

    @Test
    void page가_0이거나_숫자가_아니면_400() throws Exception {
        for (String page : new String[] {"0", "abc"}) {
            mvc.perform(get("/api/search").param("q", "보리").param("page", page))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void 응답에는_번호_뒤_4자리만_있다() throws Exception {
        SearchQuery q = SearchQuery.parse("보리");
        when(repository.count(q)).thenReturn(1L);
        when(repository.find(q, 20, 0)).thenReturn(List.of(new SearchItem(1, "보리", 2, "김보호", "0000",
                "말티즈", LocalDate.of(2023, 5, 2), true, "ACTIVE", List.of("콩이"))));
        mvc.perform(get("/api/search").param("q", "보리"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].phoneLast4").value("0000"))
                .andExpect(jsonPath("$.data.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].birthDateEstimated").value(true))
                .andExpect(jsonPath("$.data.items[0].siblingNames[0]").value("콩이"));
    }

    @Test
    void 초성_자음만_있으면_초성_검색_섞이면_이름_검색() {
        assertThat(SearchQuery.parse("ㅊ ㅋ").text()).isEqualTo("ㅊㅋ");
        assertThat(SearchQuery.parse("ㅊ ㅋ").chosung()).isTrue();
        assertThat(SearchQuery.parse("ㅊ코").chosung()).isFalse();
        assertThat(SearchQuery.parse("초코").chosung()).isFalse();
        assertThat(SearchQuery.parse("0000").chosung()).isFalse();
    }

    @Test
    void 초성_검색도_미용사가_쓰고_번호는_뒤_4자리만() throws Exception {
        SearchQuery q = SearchQuery.parse("ㅊㅋ");
        when(repository.count(q)).thenReturn(1L);
        when(repository.find(q, 20, 0)).thenReturn(List.of(new SearchItem(1, "초코", 2, "김보호", "0000",
                "말티즈", LocalDate.of(2023, 5, 2), false, "ACTIVE", List.of())));
        for (Role role : new Role[] {Role.DESK, Role.GROOMER}) {
            mvc.perform(get("/api/search").param("q", "ㅊㅋ").session(AuthTestSupport.session(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].petName").value("초코"))
                    .andExpect(jsonPath("$.data.items[0].phone").doesNotExist());
        }
    }
}
