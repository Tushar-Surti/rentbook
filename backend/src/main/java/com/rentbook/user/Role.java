package com.rentbook.user;

public enum Role {
    LANDLORD,
    TENANT,
    /** Works for one landlord on the properties they assign. */
    CARETAKER;

    /** The Spring Security authority carried by an access token for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
