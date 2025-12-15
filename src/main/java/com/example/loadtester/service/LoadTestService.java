package com.example.loadtester.service;

import com.example.loadtester.config.WorkloadConfig;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import us.abstracta.jmeter.javadsl.core.DslTestPlan;
import us.abstracta.jmeter.javadsl.core.threadgroups.DslThreadGroup;
import us.abstracta.jmeter.javadsl.core.threadgroups.BaseThreadGroup.ThreadGroupChild;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

import us.abstracta.jmeter.javadsl.core.DslTestPlan.TestPlanChild;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static us.abstracta.jmeter.javadsl.JmeterDsl.*;
import static us.abstracta.jmeter.javadsl.jdbc.JdbcJmeterDsl.*;

@Service
public class LoadTestService {

    public void runTest(String configPath) throws IOException {
        // 1. Load Configuration
        WorkloadConfig config = loadConfig(configPath);

        // 2. Build Test Plan
        DslTestPlan testPlan = buildTestPlan(config);

        // 3. Run Test
        System.out.println("Starting Load Test...");
        TestPlanStats stats = testPlan.run();

        System.out.println("Load Test Completed.");
        System.out.println("Overall Stats: " + stats.overall().samplesCount() + " samples taken.");
        System.out.println("99th Percentile: " + stats.overall().sampleTimePercentile99());
    }

    private WorkloadConfig loadConfig(String path) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = new FileInputStream(path)) {
            return yaml.loadAs(inputStream, WorkloadConfig.class);
        }
    }

    private DslTestPlan buildTestPlan(WorkloadConfig config) {
        WorkloadConfig.DatabaseConfig dbConfig = config.getDatabase();

        String poolName = "JDBC_POOL";

        // Define Database Pool
        Class<? extends java.sql.Driver> driverClass;
        try {
            driverClass = (Class<? extends java.sql.Driver>) Class.forName(dbConfig.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver class not found: " + dbConfig.getDriverClass(), e);
        }

        var pool = jdbcConnectionPool(poolName, driverClass, dbConfig.getUrl())
                .user(dbConfig.getUsername())
                .password(dbConfig.getPassword());

        List<DslThreadGroup> threadGroups = new ArrayList<>();

        // Create Thread Group for each scenario
        for (WorkloadConfig.Scenario scenario : config.getScenarios()) {

            var jdbcSampler = jdbcSampler(scenario.getName(), poolName, scenario.getQuery());

            // Define list of children for the thread group
            List<ThreadGroupChild> tgChildren = new ArrayList<>();
            if (scenario.getCsvFile() != null && !scenario.getCsvFile().isEmpty()) {
                tgChildren.add(csvDataSet(scenario.getCsvFile()));
            }
            tgChildren.add(jdbcSampler);

            // Use RPS Thread Group
            var rpsTG = rpsThreadGroup(scenario.getName())
                    .maxThreads(scenario.getMaxThreads());

            // Build the schedule
            for (Double rps : scenario.getRpsSteps()) {
                rpsTG.rampToAndHold(rps,
                        Duration.ofSeconds(scenario.getRampUpSeconds()),
                        Duration.ofSeconds(scenario.getStepDurationSeconds()));
            }

            rpsTG.children(tgChildren.toArray(new ThreadGroupChild[0]));

            threadGroups.add(rpsTG);
        }

        List<TestPlanChild> children = new ArrayList<>();
        children.add(pool);
        children.addAll(threadGroups);

        return testPlan(
                children.toArray(new TestPlanChild[0]));
    }
}
