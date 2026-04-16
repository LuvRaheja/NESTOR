package com.nestor.api.controller;

import com.nestor.common.db.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class InstrumentController {

    private static final Logger log = LoggerFactory.getLogger(InstrumentController.class);
    private final InstrumentRepository instrumentRepo;

    public InstrumentController(InstrumentRepository instrumentRepo) {
        this.instrumentRepo = instrumentRepo;
    }

    @GetMapping("/instruments")
    public List<Map<String, Object>> listInstruments(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> instruments = instrumentRepo.findAll();
        List<Map<String, Object>> simplified = new ArrayList<>();
        for (Map<String, Object> inst : instruments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("symbol", inst.get("symbol"));
            item.put("name", inst.get("name"));
            item.put("instrument_type", inst.get("instrument_type"));
            item.put("current_price", inst.get("current_price"));
            simplified.add(item);
        }
        return simplified;
    }
}
