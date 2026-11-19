package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/** The bootcamp form, field for field ({@code date}, {@code start} and {@code end} are Kampala local; fee is whole UGX). */
public record CreateBootcampRequest(
        @NotBlank @Size(max = 200) String topic,
        @NotBlank @Size(max = 200) String instructor,
        @NotNull LocalDate date,
        @NotNull LocalTime start,
        @NotNull LocalTime end,
        @NotNull @Positive Integer capacity,
        @NotNull @PositiveOrZero Integer fee) {
}
