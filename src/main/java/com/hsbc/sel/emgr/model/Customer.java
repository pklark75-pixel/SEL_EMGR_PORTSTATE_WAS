package com.hsbc.sel.emgr.model;

import java.util.ArrayList;
import java.util.List;

public class Customer {

    private String custNo;
    private String emailAddr;
    private String firstName;
    private final List<PortfolioStatement> statements = new ArrayList<PortfolioStatement>();

    public Customer(String custNo, String emailAddr, String firstName) {
        this.custNo = custNo;
        this.emailAddr = emailAddr;
        this.firstName = firstName;
    }

    public void addStatement(PortfolioStatement statement) {
        this.statements.add(statement);
    }

    public String getCustNo() { return custNo; }
    public String getEmailAddr() { return emailAddr; }
    public String getFirstName() { return firstName; }
    public List<PortfolioStatement> getStatements() { return statements; }

}

