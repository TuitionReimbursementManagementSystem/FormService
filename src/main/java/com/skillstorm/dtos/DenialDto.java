package com.skillstorm.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DenialDto {

    @NotNull
    @NotEmpty
    private String approver;

    @NotNull(message = "{denial.reason.must}")
    @NotEmpty(message = "{denial.reason.must}")
    private String reason;
}
