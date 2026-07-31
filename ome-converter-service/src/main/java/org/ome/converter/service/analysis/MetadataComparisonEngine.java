package org.ome.converter.service.analysis;

import org.ome.converter.core.model.*;

import java.nio.file.Path;
import java.util.*;

public class MetadataComparisonEngine {

    public GapAnalysisResult compare(
        String datasetName,
        OmeZarrVersion version,
        List<OriginalMetadataItem> originalItems,
        List<ConvertedMetadataItem> convertedItems,
        Path htmlReportPath
    ) {
        Map<String, ConvertedMetadataItem> convertedByKey = new LinkedHashMap<>();
        for (ConvertedMetadataItem conv : convertedItems) {
            convertedByKey.put(conv.key().toLowerCase(), conv);
        }

        int mapped = 0;
        int vendorDumped = 0;
        int loss = 0;

        List<GapAnalysisResult.GapAnalysisItemDetail> lostItems = new ArrayList<>();

        for (OriginalMetadataItem orig : originalItems) {
            String rawKey = orig.key();
            String rawKeyLower = rawKey.toLowerCase();
            Optional<String> canonicalConcept = SemanticMetadataDictionary.findCanonicalConcept(rawKey);

            if (convertedByKey.containsKey(rawKeyLower) && "VENDOR_CUSTOM".equalsIgnoreCase(convertedByKey.get(rawKeyLower).namespace())) {
                vendorDumped++;
            } else if ("TRANSITIONAL_XML".equalsIgnoreCase(orig.category())) {
                vendorDumped++;
            } else if (convertedByKey.containsKey(rawKeyLower)) {
                mapped++;
            } else if (canonicalConcept.isPresent()) {
                String concept = canonicalConcept.get();
                ConvertedMetadataItem conceptMatch = findMatchForConcept(concept, convertedItems);
                if (conceptMatch != null) {
                    mapped++;
                } else {
                    loss++;
                    lostItems.add(new GapAnalysisResult.GapAnalysisItemDetail(
                        rawKey,
                        orig.value(),
                        "LOSS (Missing)",
                        "Mapped in dictionary to '" + concept + "', but absent in output OME-Zarr attributes."
                    ));
                }
            } else if (isPossibleMatchCandidate(rawKeyLower)) {
                loss++;
                lostItems.add(new GapAnalysisResult.GapAnalysisItemDetail(
                    rawKey,
                    orig.value(),
                    "LOSS (Unmapped)",
                    "Potential hardware/acquisition attribute requiring expert review."
                ));
            } else {
                loss++;
                lostItems.add(new GapAnalysisResult.GapAnalysisItemDetail(
                    rawKey,
                    orig.value(),
                    "LOSS (Unregistered)",
                    "Unregistered raw vendor tag dropped from standard OME translation."
                ));
            }
        }

        int totalOriginal = Math.max(1, originalItems.size());

        return new GapAnalysisResult(
            datasetName,
            version != null ? version : OmeZarrVersion.OME_ZARR_0_5,
            totalOriginal,
            mapped,
            vendorDumped,
            loss,
            lostItems,
            htmlReportPath
        );
    }

    private ConvertedMetadataItem findMatchForConcept(String concept, List<ConvertedMetadataItem> convertedItems) {
        for (ConvertedMetadataItem conv : convertedItems) {
            if (conv.key().equalsIgnoreCase(concept) || conv.locationPath().toLowerCase().contains(concept.toLowerCase())) {
                return conv;
            }
            if ("pixel_size_x".equalsIgnoreCase(concept) && conv.key().equalsIgnoreCase("scale")) {
                return conv;
            }
        }
        return null;
    }

    private boolean isPossibleMatchCandidate(String rawKeyLower) {
        return rawKeyLower.contains("camera") || rawKeyLower.contains("lens") || rawKeyLower.contains("filter")
            || rawKeyLower.contains("laser") || rawKeyLower.contains("power") || rawKeyLower.contains("channel");
    }
}
