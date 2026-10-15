package com.example.ledger.forecast;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forecasts")
public class ForecastController {
    private final ForecastService service;

    public ForecastController(ForecastService service) {
        this.service = service;
    }

    @PostMapping("/monthly")
    public ForecastContracts.Response forecast(@Valid @RequestBody ForecastContracts.Request request) {
        return service.forecast(request);
    }
}
