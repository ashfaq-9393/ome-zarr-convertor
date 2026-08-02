package org.ome.converter.service.analysis.validator;

import java.util.List;
import java.util.Map;

/**
 * Interface for metadata validation components.
 * Extracted from canonical standalone repository: https://github.com/TausiqVarma/zarr-converter.git
 */
public interface MetadataValidator {

    /**
     * Validates raw metadata against an OME-XML document using the given dictionary.
     *
     * @param rawMetadata the raw metadata key-value pairs extracted by Bio-Formats
     * @param omeXml      the target OME-XML content
     * @param dictionary  the mapping dictionary to evaluate against
     * @return list of GapResult objects representing each key's verdict
     */
    List<GapResult> validate(Map<String, Object> rawMetadata, String omeXml, ValidationDictionary dictionary);
}
