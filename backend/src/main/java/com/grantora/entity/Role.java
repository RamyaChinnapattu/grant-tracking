package com.grantora.entity;

public enum Role {
    ADMIN,
    BENEFICIARY,
    OFFICER,
    FIELD_OFFICER,
    DISTRICT_OFFICER,
    FINANCE_OFFICER;

    public boolean isOfficer() {
        return this == OFFICER || this == FIELD_OFFICER || this == DISTRICT_OFFICER || this == FINANCE_OFFICER;
    }
}