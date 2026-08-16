package org.ome.converter.service.analysis;

import org.ome.converter.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class MetadataGapAnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(MetadataGapAnalyzerService.class);

    private final VsiGapAnalyzerEngine vsiEngine = new VsiGapAnalyzerEngine();
    private final OirGapAnalyzerEngine oirEngine = new OirGapAnalyzerEngine();

    public GapAnalysisResult analyzeAndReport(
        String datasetName,
        OmeZarrVersion version,
        ImageMetadata standardMeta,
        VendorMetadata vendorMeta,
        Path zarrRoot
    ) {
        log.info("Starting In-App Metadata Gap Analysis for dataset: {} ({})", datasetName, version.getDisplayName());

        Path reportPath = zarrRoot.getParent() != null ? zarrRoot.getParent() : zarrRoot;
        GapAnalysisResult finalResult;

        if (datasetName.toLowerCase().endsWith(".oir")) {
            log.info("Using OIR Gap Analysis Engine (Rulebook Mapping) for {}", datasetName);
            finalResult = oirEngine.analyze(datasetName, version, standardMeta, vendorMeta, zarrRoot);
        } else {
            log.info("Using VSI Gap Analysis Engine (Rulebook Mapping) for {}", datasetName);
            finalResult = vsiEngine.analyze(datasetName, version, standardMeta, vendorMeta, zarrRoot);
        }

        log.info("Completed In-App Metadata Gap Analysis for {}. Mapped: {}, Vendor Dumped: {}, Loss: {}",
            datasetName, finalResult.mappedCount(), finalResult.vendorDumpedCount(), finalResult.lossCount());

        return finalResult;
    }
}
