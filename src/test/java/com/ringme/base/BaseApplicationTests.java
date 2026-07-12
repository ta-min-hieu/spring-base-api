package com.ringme.base;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BaseApplicationTests {

    // Nạp toàn bộ application context. Phòng ngừa các lỗi cấu hình như @Value không resolve
    // được (ví dụ sai tiền tố thuộc tính application.*).
    @Test
    void contextLoads() {
    }

}
