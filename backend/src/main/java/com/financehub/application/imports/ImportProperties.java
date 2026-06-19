package com.financehub.application.imports;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "financehub.import")
public class ImportProperties {

    private int maxRows = 10000;
    private Duration jobTtl = Duration.ofHours(24);
    private String expiryCron = "0 0 * * * *";

    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int v) { this.maxRows = v; }
    public Duration getJobTtl() { return jobTtl; }
    public void setJobTtl(Duration v) { this.jobTtl = v; }
    public String getExpiryCron() { return expiryCron; }
    public void setExpiryCron(String v) { this.expiryCron = v; }
}
