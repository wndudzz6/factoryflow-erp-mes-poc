package com.factoryflow.mes.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;
import java.util.List;

@Schema(description = "최신순 이력 페이지. 시각 내림차순, 동일 시각은 이력 ID 내림차순")
public record HistoryPageResponse<T>(
        List<T> content,
        @Schema(description = "0부터 시작하는 페이지 번호", example = "0") int page,
        @Schema(description = "페이지 크기 (1~100)", example = "20") int size,
        long totalElements,
        int totalPages
) {
    public static <T> HistoryPageResponse<T> from(Page<T> page) {
        return new HistoryPageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
