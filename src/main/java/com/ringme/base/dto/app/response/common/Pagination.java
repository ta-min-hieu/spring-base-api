package com.ringme.base.dto.app.response.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Pagination {
    private Integer page;
    private Integer size;
    private Integer totalElements;
    private Integer totalPages;
}
