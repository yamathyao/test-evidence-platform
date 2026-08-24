package com.talkanything.testevidence.platform.shared;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T, R> PageResponse<R> from(Page<T> value, Function<T, R> mapper) {
        return new PageResponse<R>(value.getContent().stream().map(mapper).toList(), value.getNumber(),
                value.getSize(), value.getTotalElements(), value.getTotalPages());
    }
}
