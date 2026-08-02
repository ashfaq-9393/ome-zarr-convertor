package org.ome.converter.service.analysis.validator;

import java.util.List;
import java.util.Map;

/**
 * Interface for a Gap Analysis engine.
 * Extracted from canonical standalone repository: https://github.com/TausiqVarma/zarr-converter.git
 */
public interface GapAnalyzer {

    /**
     * Evaluates raw metadata against OME-XML content using a mapping dictionary.
     *
     * @param rawMetadata the raw metadata key-value pairs
     * @param omeXml      the full OME-XML string
     * @return a list of gap results detailing the validation status of each key
     */
    List<GapResult> analyze(Map<String, Object> rawMetadata, String omeXml);
}
