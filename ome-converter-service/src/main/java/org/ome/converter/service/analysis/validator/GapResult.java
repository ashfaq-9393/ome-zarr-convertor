package org.ome.converter.service.analysis.validator;

/**
 * Immutable data class representing the result of a single gap analysis check.
 * Each instance represents one raw metadata key and its validation outcome.
 * Extracted from canonical standalone repository: https://github.com/TausiqVarma/zarr-converter.git
 */
public class GapResult {

    public enum Verdict {
        /** Value was formally mapped into a structured OME-XML element */
        PRESERVED_FORMAL,
        /** Value was dumped into StructuredAnnotations as OriginalMetadata */
        PRESERVED_RAW,
        /** Value existed in OIR but is MISSING from the OME-XML entirely */
        CRITICAL_LOSS,
        /** Structural/container key — no data to validate */
        SKIPPED,
        /** Key was not in our dictionary at all */
        UNTRACKED
    }

    private final String rawKey;
    private final String rawValue;
    private final String dictionaryKey;
    private final String status;
    private final String omeTarget;
    private final String foundValue;
    private final Verdict verdict;

    public GapResult(String rawKey, String rawValue, String dictionaryKey,
                     String status, String omeTarget, String foundValue, Verdict verdict) {
        this.rawKey = rawKey;
        this.rawValue = rawValue;
        this.dictionaryKey = dictionaryKey;
        this.status = status;
        this.omeTarget = omeTarget;
        this.foundValue = foundValue;
        this.verdict = verdict;
    }

    public String getRawKey()        { return rawKey; }
    public String getRawValue()      { return rawValue; }
    public String getDictionaryKey() { return dictionaryKey; }
    public String getStatus()        { return status; }
    public String getOmeTarget()     { return omeTarget; }
    public String getFoundValue()    { return foundValue; }
    public Verdict getVerdict()      { return verdict; }

    @Override
    public String toString() {
        return String.format("[%s] %s = %s -> %s", verdict, rawKey, rawValue, foundValue);
    }
}
