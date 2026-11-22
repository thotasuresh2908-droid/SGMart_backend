package com.sgmart.inventory;

import com.sgmart.inventory.StockReportDtos.StockReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class AdminStockController {

    private final StockReportService reports;

    public AdminStockController(StockReportService reports) {
        this.reports = reports;
    }

    /** Admin > Today's stock. Optional ?date=2026-11-12 for an earlier day (India time). */
    @GetMapping("/api/v1/admin/reports/stock")
    public StockReport stock(@RequestParam(name = "date", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.report(date);
    }
}
