package org.ome.converter.service.analysis.validator;

/**
 * Interface representing a dictionary of mapping rules for Gap Analysis.
 * Extracted from canonical standalone repository: https://github.com/TausiqVarma/zarr-converter.git
 */
public interface ValidationDictionary {
    /**
     * Looks up the corresponding rule for a given raw OIR key.
     * 
     * @param rawKey the key from the raw metadata map
     * @return the validation rule, or null if no rule exists
     */
    ValidationRule findRule(String rawKey);
}
