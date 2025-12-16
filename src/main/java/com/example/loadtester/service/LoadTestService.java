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
import org.apache.http.entity.ContentType;

@Service
public class LoadTestService {

    public void runTest(String configPath) throws IOException {
        // 1. Load Configuration
        WorkloadConfig config = loadConfig(configPath);

        // 2. Build Test Plan
        DslTestPlan testPlan = buildTestPlan(config);

        // 3. Run Test
        System.out.println("Starting Load Test...");
        testPlan.saveAsJmx("debug.jmx");
        System.out.println("DEBUG: Saved test plan to debug.jmx");

        // Calculate estimated duration
        long totalDurationSeconds = config.getScenarios().stream()
                .mapToLong(s -> s.getRpsSteps().size() *
                        (s.getRampUpSeconds() + s.getStepDurationSeconds()))
                .max().orElse(60);

        try {
            java.util.concurrent.CompletableFuture<TestPlanStats> future = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return testPlan.run();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

            // Add buffer to timeout
            TestPlanStats stats = future.get(totalDurationSeconds + 30, java.util.concurrent.TimeUnit.SECONDS);

            System.out.println("Load Test Completed.");
            System.out.println("Overall Stats: " + stats.overall().samplesCount() + " samples taken.");
            System.out.println("99th Percentile: " + stats.overall().sampleTimePercentile99());
        } catch (java.util.concurrent.TimeoutException e) {
            System.err.println("Load Test Timed Out! forcing exit.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private WorkloadConfig loadConfig(String path) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = new FileInputStream(path)) {
            return yaml.loadAs(inputStream, WorkloadConfig.class);
        }
    }

    private DslTestPlan buildTestPlan(WorkloadConfig config) {

        List<DslThreadGroup> threadGroups = new ArrayList<>();
        List<TestPlanChild> testPlanChildren = new ArrayList<>();

        String poolName = "JDBC_POOL";
        boolean hasJdbc = config.getScenarios().stream()
                .anyMatch(s -> s.getType() == WorkloadConfig.Scenario.Protocol.JDBC);

        if (hasJdbc && config.getDatabase() != null) {
            WorkloadConfig.DatabaseConfig dbConfig = config.getDatabase();
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
            testPlanChildren.add(pool);
        }

        // Create Thread Group for each scenario
        for (WorkloadConfig.Scenario scenario : config.getScenarios()) {

            // Define list of children for the thread group
            List<ThreadGroupChild> tgChildren = new ArrayList<>();
            if (scenario.getCsvFile() != null && !scenario.getCsvFile().isEmpty()) {
                tgChildren.add(csvDataSet(scenario.getCsvFile()));
            }

            if (scenario.getType() == WorkloadConfig.Scenario.Protocol.JDBC) {
                var jdbcSampler = jdbcSampler(scenario.getName(), poolName, scenario.getTarget());
                System.out.println("DEBUG: Configuring JDBC Sampler for: " + scenario.getName());
                System.out.println("DEBUG: Query: [" + scenario.getTarget() + "]");
                if (scenario.getParams() != null) {
                    System.out.println("DEBUG: Found " + scenario.getParams().size() + " parameters.");
                    for (WorkloadConfig.Scenario.SqlParameter param : scenario.getParams()) {
                        System.out.println("DEBUG: Adding param: " + param.getValue() + " type: " + param.getType());
                        jdbcSampler = jdbcSampler.param(param.getValue(), getSqlType(param.getType()));
                    }
                } else {
                    System.out.println("DEBUG: No parameters found.");
                }

                // DEBUG: Add a pre-processor to print variables
                tgChildren.add(jsr223Sampler(s -> {
                    System.out.println("RUNTIME DEBUG: id=" + s.vars.get("id"));
                    System.out.println("RUNTIME DEBUG: name=" + s.vars.get("name"));
                    System.out.println("RUNTIME DEBUG: email=" + s.vars.get("email"));
                }));

                tgChildren.add(jdbcSampler);
            } else if (scenario.getType() == WorkloadConfig.Scenario.Protocol.HTTP) {
                var httpSampler = httpSampler(scenario.getName(), scenario.getTarget())
                        .method(scenario.getMethod());
                if (scenario.getBody() != null) {
                    httpSampler.body(scenario.getBody())
                            .contentType(ContentType.create(scenario.getContentType()));
                }
                tgChildren.add(httpSampler);
            }

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
            rpsTG.showInGui();

            threadGroups.add(rpsTG);
        }

        testPlanChildren.addAll(threadGroups);

        testPlanChildren.add(htmlReporter("target/jmeter-report"));

        return testPlan(
                testPlanChildren.toArray(new TestPlanChild[0]));
    }

    private int getSqlType(String type) {
        if (type == null) {
            return java.sql.Types.VARCHAR;
        }
        switch (type.toUpperCase()) {
            case "INTEGER":
                return java.sql.Types.INTEGER;
            case "BIGINT":
                return java.sql.Types.BIGINT;
            case "DOUBLE":
                return java.sql.Types.DOUBLE;
            case "DECIMAL":
                return java.sql.Types.DECIMAL;
            case "NUMERIC":
                return java.sql.Types.NUMERIC;
            case "BOOLEAN":
                return java.sql.Types.BOOLEAN;
            case "DATE":
                return java.sql.Types.DATE;
            case "TIMESTAMP":
                return java.sql.Types.TIMESTAMP;
            case "VARCHAR":
            default:
                return java.sql.Types.VARCHAR;
        }
    }
}
