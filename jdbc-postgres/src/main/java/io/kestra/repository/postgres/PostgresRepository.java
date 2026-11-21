package io.kestra.repository.postgres;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.JdbcJsonbUtils;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import lombok.SneakyThrows;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.KEY_FIELD;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.VALUE_FIELD;

@Requires(condition = PostgresRepository.PostgresCondition.class)
@Requires(property = "kestra.server-type", notEquals = "WORKER")
@EachBean(JdbcTableConfig.class)
public class PostgresRepository<T> extends io.kestra.jdbc.AbstractJdbcRepository<T> {

    @Inject
    public PostgresRepository(@Parameter JdbcTableConfig jdbcTableConfig,
        JooqDSLContextWrapper dslContextWrapper) {
        super(jdbcTableConfig, dslContextWrapper);
    }

    @Override
    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.noCondition();
        }

        if (fields.size() > 1) {
            throw new IllegalArgumentException("Invalid fullTextCondition" + fields);
        }

        return DSL.condition(fields.getFirst() + " @@ FULLTEXT_SEARCH(?)", query);
    }

    @SneakyThrows
    @Override
    public Map<Field<Object>, Object> persistFields(T entity) {
        String json = MAPPER.writeValueAsString(entity);
        Map<Field<Object>, Object> fields = HashMap.newHashMap(1);
        fields.put(VALUE_FIELD, DSL.val(JdbcJsonbUtils.valueOf(json)));
        return fields;
    }

    @SneakyThrows
    @Override
    public void persist(T entity, DSLContext context, @Nullable Map<Field<Object>, Object> fields) {
        Map<Field<Object>, Object> finalFields = fields == null ? this.persistFields(entity) : fields;

        context
            .insertInto(table)
            .set(KEY_FIELD, key(entity))
            .set(finalFields)
            .onConflict(KEY_FIELD)
            .doUpdate()
            .set(excluded(finalFields))
            .execute();
    }

    @Override
    protected InsertOnDuplicateSetMoreStep<Record> buildInsertRequest(T entity, Map<Field<Object>, Object> fields,
        DSLContext dslContext) {

        return dslContext
            .insertInto(table)
            .set(KEY_FIELD, key(entity))
            .set(fields)
            .onConflict(KEY_FIELD)
            .doUpdate()
            .set(excluded(fields));
    }

    @Override
    public <R extends Record, E> ArrayListTotal<E> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper) {
        if (pageable == null || pageable.getSize() == -1) {
            Result<R> records = this.sort(select, pageable).fetch();
            return new ArrayListTotal<>(records.map(mapper), records.size());
        }

        int totalCount = context.fetchCount(select);
        Result<R> records = this.pageable(select, pageable).fetch();

        if (records.isEmpty()) {
            return new ArrayListTotal<>(List.of(), 0);
        }

        // Count and page are separate statements, so a row committed in between must not leave the total below what was returned.
        long returnedUpTo = pageable.getOffset() - pageable.getSize() + records.size();
        return new ArrayListTotal<>(records.map(mapper), Math.max(totalCount, returnedUpTo));
    }

    @Override
    public <R extends Record> T map(R record) {
        if (deserializer != null) {
            return deserializer.apply(record);
        } else {
            return this.deserialize(record.get("value", JSONB.class).data());
        }
    }

    static class PostgresCondition implements io.micronaut.context.condition.Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return ((Optional<String>) context.get("kestra.repository.type", String.class)).map(it -> "postgres".equals(it)).orElse(false);
        }
    }
}
