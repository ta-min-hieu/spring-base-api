package com.ringme.base.dto.app.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetTokensResponse {
    private String accessToken;
    private String refreshToken;
}
