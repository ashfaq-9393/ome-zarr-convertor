package org.ome.converter.service.analysis.validator;

/**
 * Represents a single mapping rule for Gap Analysis.
 * Extracted from canonical standalone repository: https://github.com/TausiqVarma/zarr-converter.git
 */
public interface ValidationRule {
    String getDictionaryKey();
    String getStatus();
    String getOmeTarget();
    String getXmlTag();
    String getXmlAttribute();
}
