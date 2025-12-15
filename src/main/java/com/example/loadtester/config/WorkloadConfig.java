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
        private int threads;
        private int rampUpSeconds;
        private int durationSeconds;
    }
}
