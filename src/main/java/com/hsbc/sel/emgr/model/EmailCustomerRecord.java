package com.hsbc.sel.emgr.model;

public class EmailCustomerRecord {

    private final String customerNo;
    private final String email;
    private final String firstName;

    public EmailCustomerRecord(String customerNo, String email, String firstName) {
        this.customerNo = customerNo;
        this.email = email;
        this.firstName = firstName;
    }

    public String getCustomerNo() { return customerNo; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
}

