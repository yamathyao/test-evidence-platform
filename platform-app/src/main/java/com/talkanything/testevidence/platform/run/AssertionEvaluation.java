package com.talkanything.testevidence.platform.run;

public record AssertionEvaluation(AssertionResultStatus status, String expectedJson, String actualJson,
                                  String failureReason) {
}
