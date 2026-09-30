package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.dto.response.FinanceInsightsResponse;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.service.InsightService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/insights")
@Tag(name = "Finance - Insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    /** Actionable nudges for a month, in one currency (mixing KHR and USD would be meaningless). */
    @GetMapping
    public ResponseEntity<ApiResponse<FinanceInsightsResponse>> insights(
        @RequestParam(required = false) Currency currency,
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year) {
        LocalDate now = LocalDate.now();
        Currency c = currency == null ? Currency.USD : currency;
        int m = month == null ? now.getMonthValue() : month;
        int y = year == null ? now.getYear() : year;
        return ResponseEntity.ok(ApiResponse.success("Insights retrieved",
            insightService.getInsights(c, m, y)));
    }
}
