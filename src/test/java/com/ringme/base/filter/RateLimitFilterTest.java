package com.ringme.base.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử bộ lọc giới hạn request đầu vào THEO IP.
 * Hạ ngưỡng xuống 2 lượt/phút để dễ test: mỗi IP có quota riêng.
 */
@SpringBootTest(properties = {
        "resilience4j.ratelimiter.configs.inbound.limit-for-period=2",
        "resilience4j.ratelimiter.configs.inbound.limit-refresh-period=1m",
        "resilience4j.ratelimiter.configs.inbound.timeout-duration=0"
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    /** Giả lập IP client cho request (vì MockMvc mặc định luôn dùng 127.0.0.1). */
    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void exceedsLimitForOneIp_returns429() throws Exception {
        // 2 lượt đầu của IP này còn quota -> qua được (200).
        mockMvc.perform(get("/troubleshoot/ping").with(fromIp("10.0.0.1"))).andExpect(status().isOk());
        mockMvc.perform(get("/troubleshoot/ping").with(fromIp("10.0.0.1"))).andExpect(status().isOk());

        // Lượt thứ 3 cùng IP hết quota -> 429 + body Response chuẩn (code "429").
        mockMvc.perform(get("/troubleshoot/ping").with(fromIp("10.0.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("429"));
    }

    @Test
    void differentIp_hasSeparateQuota() throws Exception {
        // IP A xài hết quota tới mức bị 429.
        mockMvc.perform(get("/troubleshoot/ping").with(fromIp("10.0.0.2"))).andExpect(status().isOk());
        mockMvc.perform(get("/troubleshoot/ping").with(fromIp("10.0.0.2"))).andExpect(status().isOk());
        mockMvc.perform(get("/troubleshoot/ping").with(fromIp("10.0.0.2"))).andExpect(status().isTooManyRequests());

        // IP B (khác) vẫn còn nguyên quota -> KHÔNG bị ảnh hưởng bởi IP A.
        mockMvc.perform(get("/troubleshoot/ping").with(fromIp("10.0.0.3"))).andExpect(status().isOk());
    }

    @Test
    void excludedPath_isNotRateLimited() throws Exception {
        // /actuator/health nằm trong excluded-paths -> gọi nhiều lần vẫn không bị 429.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/actuator/health").with(fromIp("10.0.0.9"))).andExpect(status().isOk());
        }
    }
}
