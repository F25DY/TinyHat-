package com.hexora.hexora.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
    @NotNull ReportType tipo,
    @NotBlank @Size(max = 200) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String titulo
) {
}
