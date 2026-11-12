package org.unipod.unipoderpbackend.controller.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.config.ConfigResponse;
import org.unipod.unipoderpbackend.service.config.ConfigService;

@RestController
public class ConfigController {
    private final ConfigService config;

    public ConfigController(ConfigService config) {
        this.config = config;
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        return config.view();
    }
}
