package com.hsbc.sel.emgr.jdk;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.hsbc.sel.emgr.config.PfsProperties;

public final class PfsDbConnectionTest {

    private PfsDbConnectionTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== PFS DB Connection Test ===");

        PfsProperties props = PfsProperties.loadDefault();

        System.out.println("URL   : " + props.getDbUrl());
        System.out.println("User  : " + props.getDbUser());

        String password;
        try {
            password = props.getEffectiveDbPassword();
            System.out.println("Key   : resolved OK");
        } catch (Exception ex) {
            System.out.println("Key   : FAILED - " + ex.getMessage());
            return;
        }

        System.out.println("Driver: " + props.getDbDriverClass());
        System.out.println("Loading driver...");
        Class.forName(props.getDbDriverClass());
        System.out.println("Driver loaded.");

        System.out.println("Connecting...");
        Connection conn = DriverManager.getConnection(props.getDbUrl(), props.getDbUser(), password);
        System.out.println("Connection OK: autoCommit=" + conn.getAutoCommit());

        System.out.println("Running test query: SELECT CURRENT_TIMESTAMP");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT CURRENT_TIMESTAMP");
        if (rs.next()) {
            System.out.println("DB Server Time: " + rs.getString(1));
        }

        rs.close();
        stmt.close();
        conn.close();
        System.out.println("=== TEST PASSED ===");
    }
}

