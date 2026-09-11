package com.rentbook.user;

public enum Role {
    LANDLORD,
    TENANT;

    /** The Spring Security authority carried by an access token for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
