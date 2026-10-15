package com.example.ledger.advice;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-advice")
public class AdviceController {
    private final AdviceService service;

    public AdviceController(AdviceService service) {
        this.service = service;
    }

    @PostMapping
    public AdviceContracts.Response advise(@Valid @RequestBody AdviceContracts.Request request) {
        return service.advise(request);
    }
}
