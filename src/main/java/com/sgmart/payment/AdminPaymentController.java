package com.sgmart.payment;

import com.sgmart.payment.PaymentReportDtos.PaymentReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class AdminPaymentController {

    private final PaymentReportService reports;

    public AdminPaymentController(PaymentReportService reports) {
        this.reports = reports;
    }

    /** Admin > Payments. Today by default; ?date=2026-11-12 for another day; ?all=true for All time. */
    @GetMapping("/api/v1/admin/reports/payments")
    public PaymentReport payments(@RequestParam(name = "date", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam(name = "all", defaultValue = "false") boolean all) {
        return reports.report(date, all);
    }
}
