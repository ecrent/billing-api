package com.ecren.billing.dto.response;

import java.util.List;

public record UsageSummaryResponse(List<MetricSummary> metrics) {
    public record MetricSummary(String metric, long consumed, long included, long overage) {}
}
