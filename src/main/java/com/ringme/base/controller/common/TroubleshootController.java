package com.ringme.base.controller.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ringme.base.config.app.AppConfig;

@Log4j2
@RestController
@RequestMapping("/troubleshoot")
@RequiredArgsConstructor
public class TroubleshootController {

    private final AppConfig appConfig;

    @GetMapping("/ping")
    public String ping() {
        log.info("PING REQUEST | appConfig: {}", appConfig);
        return "pong";
    }
}
