package com.ecren.billing.dto.response;

import java.time.LocalDate;

public record TimeAdvanceResponse(LocalDate today, int cyclesProcessed, String message) {}
