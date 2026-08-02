package org.ome.converter.service.analysis.validator;

import java.util.List;
import java.util.Map;

/**
 * Validates OIR metadata against OME-XML using the GapAnalysisEngine.
 * Extracted from canonical standalone repository: https://github.com/TausiqVarma/zarr-converter.git
 */
public class BioFormatsGapValidator implements MetadataValidator {

    @Override
    public List<GapResult> validate(Map<String, Object> rawMetadata, String omeXml, ValidationDictionary dictionary) {
        GapAnalyzer engine = new GapAnalysisEngine(dictionary);
        return engine.analyze(rawMetadata, omeXml);
    }
}
