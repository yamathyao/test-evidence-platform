package com.talkanything.testevidence.platform.casefile;

import java.util.UUID;
import com.talkanything.testevidence.platform.shared.NotFoundException;

public class TestCaseNotFoundException extends NotFoundException {
    TestCaseNotFoundException(UUID id) { super("Test case not found: " + id); }
}
