package com.mednet.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaginationTest {

    @Test
    void rejectsInvalidPageAndSizeInsteadOfCoercingThem() {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

        assertBadRequest(-1, 25, sort);
        assertBadRequest(0, 0, sort);
        assertBadRequest(0, -1, sort);
        assertBadRequest(0, 101, sort);
    }

    @Test
    void acceptsValidLimitsAndPreservesTheRequestedPageAndSize() {
        Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

        var firstPage = Pagination.pageRequest(0, 1, sort);
        assertThat(firstPage.getPageNumber()).isZero();
        assertThat(firstPage.getPageSize()).isEqualTo(1);
        assertThat(firstPage.getSort()).isEqualTo(sort);

        var maximumPage = Pagination.pageRequest(3, 100, sort);
        assertThat(maximumPage.getPageNumber()).isEqualTo(3);
        assertThat(maximumPage.getPageSize()).isEqualTo(100);
    }

    private static void assertBadRequest(int page, int size, Sort sort) {
        assertThatThrownBy(() -> Pagination.pageRequest(page, size, sort))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
