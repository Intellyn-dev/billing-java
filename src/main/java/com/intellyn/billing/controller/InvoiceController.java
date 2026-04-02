package com.intellyn.billing.controller;

import com.intellyn.billing.dto.InvoiceRequest;
import com.intellyn.billing.model.Invoice;
import com.intellyn.billing.service.InvoiceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Invoice createInvoice(@RequestBody InvoiceRequest request) {
        return invoiceService.generateInvoice(request);
    }

    @GetMapping("/{id}")
    public Invoice getInvoice(@PathVariable String id) {
        return invoiceService.getInvoice(id);
    }
}
