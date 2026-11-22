package com.grantora.dto;
import java.time.LocalDate;
public record FundAnalyticsFilter(LocalDate from,LocalDate to,Long schemeId,String state,String district,String disbursementStatus) {}
