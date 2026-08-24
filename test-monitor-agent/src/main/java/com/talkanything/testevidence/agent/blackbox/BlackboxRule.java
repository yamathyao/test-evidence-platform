package com.talkanything.testevidence.agent.blackbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlackboxRule {
    private final String ruleId;
    private final String runId;
    private final String caseId;
    private final String profileId;
    private final int profileVersion;
    private final long expiresAtMillis;
    private final List<Matcher> matchers;
    private final boolean payloadCaptureEnabled;

    public BlackboxRule(String ruleId, String runId, String caseId, String profileId, int profileVersion,
                        long expiresAtMillis, List<Matcher> matchers) {
        this(ruleId, runId, caseId, profileId, profileVersion, expiresAtMillis, matchers, false);
    }
    public BlackboxRule(String ruleId, String runId, String caseId, String profileId, int profileVersion,
                        long expiresAtMillis, List<Matcher> matchers, boolean payloadCaptureEnabled) {
        this.ruleId = ruleId;
        this.runId = runId;
        this.caseId = caseId;
        this.profileId = profileId;
        this.profileVersion = profileVersion;
        this.expiresAtMillis = expiresAtMillis;
        this.matchers = Collections.unmodifiableList(new ArrayList<Matcher>(matchers));
        this.payloadCaptureEnabled = payloadCaptureEnabled;
    }

    public String ruleId() { return ruleId; }
    public String runId() { return runId; }
    public String caseId() { return caseId; }
    public String profileId() { return profileId; }
    public int profileVersion() { return profileVersion; }
    public long expiresAtMillis() { return expiresAtMillis; }
    public List<Matcher> matchers() { return matchers; }
    public boolean payloadCaptureEnabled() { return payloadCaptureEnabled; }
    public boolean expired(long nowMillis) { return expiresAtMillis <= nowMillis; }

    public static final class Matcher {
        private final String field;
        private final List<String> locations;
        private final String value;

        public Matcher(String field, List<String> locations, String value) {
            this.field = field;
            this.locations = Collections.unmodifiableList(new ArrayList<String>(locations));
            this.value = value;
        }

        public String field() { return field; }
        public List<String> locations() { return locations; }
        public String value() { return value; }
    }
}
