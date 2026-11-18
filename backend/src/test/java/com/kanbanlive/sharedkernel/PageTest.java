package com.kanbanlive.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.application.Page;
import com.kanbanlive.sharedkernel.application.PageQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageTest {

    @Test
    void computesPagination() {
        Page<Integer> p = new Page<>(List.of(1, 2), 0, 2, 5);
        assertThat(p.totalPages()).isEqualTo(3);
        assertThat(p.hasNext()).isTrue();
        assertThat(new Page<>(List.of(5), 2, 2, 5).hasNext()).isFalse();
    }

    @Test
    void mapKeepsMetadata() {
        Page<String> p = new Page<>(List.of(1, 2), 1, 2, 4).map(String::valueOf);
        assertThat(p.content()).containsExactly("1", "2");
        assertThat(p.page()).isEqualTo(1);
        assertThat(p.totalElements()).isEqualTo(4);
    }

    @Test
    void pageQueryValidatesAndComputesOffset() {
        assertThat(new PageQuery(2, 10).offset()).isEqualTo(20);
        assertThatThrownBy(() -> new PageQuery(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
