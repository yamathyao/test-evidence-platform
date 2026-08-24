package com.talkanything.testevidence.platform.casefile;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assertion")
public class Assertion {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "test_case_id", nullable = false) private TestCase testCase;
    @Column(name = "sequence_no", nullable = false) private int sequenceNo;
    @Column(name = "assertion_type", nullable = false) private String assertionType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb") private String definitionJson;

    protected Assertion() { }
    Assertion(TestCase testCase, int sequenceNo, String assertionType, String definitionJson) {
        this.id = UUID.randomUUID(); this.testCase = testCase; this.sequenceNo = sequenceNo;
        this.assertionType = assertionType; this.definitionJson = definitionJson;
    }
    public int getSequenceNo() { return sequenceNo; }
    public String getAssertionType() { return assertionType; }
    public String getDefinitionJson() { return definitionJson; }
}
