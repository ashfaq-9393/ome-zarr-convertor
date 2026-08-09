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
        List<GapAnalysisResult.GapAnalysisItemDetail> allItems = new ArrayList<>();

        for (OriginalMetadataItem orig : originalItems) {
            String rawKey = orig.key();
            String rawKeyLower = rawKey.toLowerCase();
            Optional<String> canonicalConcept = SemanticMetadataDictionary.findCanonicalConcept(rawKey);

            if (convertedByKey.containsKey(rawKeyLower) && "VENDOR_CUSTOM".equalsIgnoreCase(convertedByKey.get(rawKeyLower).namespace())) {
                vendorDumped++;
                allItems.add(new GapAnalysisResult.GapAnalysisItemDetail(rawKey, orig.value(), "VENDOR_DUMPED", "Preserved raw vendor attribute in custom annotation namespace."));
            } else if ("TRANSITIONAL_XML".equalsIgnoreCase(orig.category())) {
                vendorDumped++;
                allItems.add(new GapAnalysisResult.GapAnalysisItemDetail(rawKey, orig.value(), "VENDOR_DUMPED", "Raw XML header stored in custom metadata."));
            } else if (convertedByKey.containsKey(rawKeyLower)) {
                mapped++;
                allItems.add(new GapAnalysisResult.GapAnalysisItemDetail(rawKey, orig.value(), "MAPPED", "Mapped to formal OME-XML attribute."));
            } else if (canonicalConcept.isPresent()) {
                String concept = canonicalConcept.get();
                ConvertedMetadataItem conceptMatch = findMatchForConcept(concept, convertedItems);
                if (conceptMatch != null) {
                    mapped++;
                    allItems.add(new GapAnalysisResult.GapAnalysisItemDetail(rawKey, orig.value(), "MAPPED", "Mapped via concept '" + concept + "'."));
                } else {
                    loss++;
                    GapAnalysisResult.GapAnalysisItemDetail item = new GapAnalysisResult.GapAnalysisItemDetail(
                        rawKey, orig.value(), "LOSS (Missing)", "Mapped in dictionary to '" + concept + "', but absent in output OME-Zarr attributes."
                    );
                    lostItems.add(item);
                    allItems.add(item);
                }
            } else if (isPossibleMatchCandidate(rawKeyLower)) {
                loss++;
                GapAnalysisResult.GapAnalysisItemDetail item = new GapAnalysisResult.GapAnalysisItemDetail(
                    rawKey, orig.value(), "LOSS (Unmapped)", "Potential hardware/acquisition attribute requiring expert review."
                );
                lostItems.add(item);
                allItems.add(item);
            } else {
                loss++;
                GapAnalysisResult.GapAnalysisItemDetail item = new GapAnalysisResult.GapAnalysisItemDetail(
                    rawKey, orig.value(), "LOSS (Unregistered)", "Unregistered raw vendor tag dropped from standard OME translation."
                );
                lostItems.add(item);
                allItems.add(item);
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
            allItems,
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
