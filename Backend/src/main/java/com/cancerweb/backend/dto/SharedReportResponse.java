package com.cancerweb.backend.dto;

import com.cancerweb.backend.model.Prediction;

/** A report a patient shared with the signed-in doctor, with the patient's name. */
public record SharedReportResponse(Prediction report, UserSummary patient) {
}
