package org.ome.converter.service.validation;

public record ComplianceIssue(
    ComplianceCategory category,
    ComplianceSeverity severity,
    String rule,
    String problem,
    String expected,
    String actual
) {
    public static ComplianceIssue pass(ComplianceCategory category, String rule, String explanation) {
        return new ComplianceIssue(category, ComplianceSeverity.PASS, rule, explanation, "Valid", "Valid");
    }

    public static ComplianceIssue info(ComplianceCategory category, String rule, String problem, String expected, String actual) {
        return new ComplianceIssue(category, ComplianceSeverity.INFO, rule, problem, expected, actual);
    }

    public static ComplianceIssue warning(ComplianceCategory category, String rule, String problem, String expected, String actual) {
        return new ComplianceIssue(category, ComplianceSeverity.WARNING, rule, problem, expected, actual);
    }

    public static ComplianceIssue error(ComplianceCategory category, String rule, String problem, String expected, String actual) {
        return new ComplianceIssue(category, ComplianceSeverity.ERROR, rule, problem, expected, actual);
    }
}
