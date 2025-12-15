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
        private String name;
        private String query;
        private String csvFile;
        // private int threads; // Deprecated/Removed in favor of dynamic RPS

        private int maxThreads = 50; // Default limit
        private List<Double> rpsSteps; // List of target RPS values
        private int rampUpSeconds = 5; // Ramp up time to reach each step
        private int stepDurationSeconds = 10; // Duration to hold each step
    }
}
