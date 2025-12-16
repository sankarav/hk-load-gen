package com.example.loadtester.config;

import lombok.Data;
import java.util.List;

@Data
public class WorkloadConfig {
    private DatabaseConfig database;
    private List<Scenario> scenarios;

    @Data
    public static class DatabaseConfig {
        private String url;
        private String username;
        private String password;
        private String driverClass;
    }

    @Data
    public static class Scenario {
        public enum Protocol {
            JDBC, HTTP
        }

        private String name;
        private Protocol type = Protocol.JDBC; // Default to JDBC for backward compat
        private String target; // Replaces 'query', serves as SQL or URL

        // HTTP specific
        private String method = "GET";
        private String body;
        private String contentType = "application/json";

        // Legacy setter to support 'query' in yaml for backward compatibility or ease
        // of use
        public void setQuery(String query) {
            this.target = query;
        }

        public String getQuery() {
            return target;
        }

        private String csvFile;
        // private int threads; // Deprecated/Removed in favor of dynamic RPS

        private int maxThreads = 50; // Default limit
        private List<Double> rpsSteps; // List of target RPS values
        private int rampUpSeconds = 5; // Ramp up time to reach each step
        private int stepDurationSeconds = 10; // Duration to hold each step
    }
}
