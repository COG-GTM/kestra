package io.kestra.repository.postgres;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.AbstractExecutionRepositoryTest;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgresExecutionRepositoryTest extends AbstractExecutionRepositoryTest {
    @Test
    void shouldReturnTotalOfAllMatchesForSortedLastPageAndZeroPastIt() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        inject(tenant);
        Sort sort = Sort.of(Sort.Order.desc("start_date"));

        ArrayListTotal<Execution> lastPage = executionRepository.find(Pageable.from(3, 10, sort), tenant, null);
        ArrayListTotal<Execution> pastLastPage = executionRepository.find(Pageable.from(4, 10, sort), tenant, null);
        ArrayListTotal<Execution> unpaged = executionRepository.find(Pageable.UNPAGED.withSort(sort), tenant, null);

        assertThat(lastPage.getTotal()).isEqualTo(29L);
        assertThat(lastPage).hasSize(9);
        assertThat(pastLastPage.getTotal()).isZero();
        assertThat(pastLastPage).isEmpty();
        assertThat(unpaged.getTotal()).isEqualTo(29L);
        assertThat(unpaged).hasSize(29);
    }
}
