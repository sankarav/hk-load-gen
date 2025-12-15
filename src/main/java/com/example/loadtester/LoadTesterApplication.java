package com.example.loadtester;

import com.example.loadtester.service.LoadTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoadTesterApplication implements CommandLineRunner {

    @Autowired
    private LoadTestService loadTestService;

    public static void main(String[] args) {
        SpringApplication.run(LoadTesterApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String configPath = "workload.yaml"; // Default
        if (args.length > 0) {
            configPath = args[0];
        }
        
        try {
            loadTestService.runTest(configPath);
        } catch (Exception e) {
            System.err.println("Error running load test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
