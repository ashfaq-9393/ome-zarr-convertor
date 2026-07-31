package org.ome.converter.service.analysis;

import org.ome.converter.core.model.ImageMetadata;
import org.ome.converter.core.model.OriginalMetadataItem;
import org.ome.converter.core.model.VendorMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OriginalMetadataCollector {

    public List<OriginalMetadataItem> collectOriginalMetadata(ImageMetadata standardMeta, VendorMetadata vendorMeta) {
        List<OriginalMetadataItem> list = new ArrayList<>();
        String vendorName = vendorMeta != null ? vendorMeta.formatName() : "Olympus CellSens VSI";

        // Standard OME metadata fields
        if (standardMeta != null) {
            list.add(new OriginalMetadataItem("OME.Image.SizeX", "SizeX", String.valueOf(standardMeta.sizeX()), "SPATIAL", vendorName));
            list.add(new OriginalMetadataItem("OME.Image.SizeY", "SizeY", String.valueOf(standardMeta.sizeY()), "SPATIAL", vendorName));
            list.add(new OriginalMetadataItem("OME.Image.SizeZ", "SizeZ", String.valueOf(standardMeta.sizeZ()), "SPATIAL", vendorName));
            list.add(new OriginalMetadataItem("OME.Image.SizeC", "SizeC", String.valueOf(standardMeta.sizeC()), "CHANNEL", vendorName));
            list.add(new OriginalMetadataItem("OME.Image.SizeT", "SizeT", String.valueOf(standardMeta.sizeT()), "TIME", vendorName));

            list.add(new OriginalMetadataItem("OME.Image.PhysicalSizeX", "PhysicalSizeX", String.valueOf(standardMeta.physicalSizeX()), "SPATIAL", vendorName));
            list.add(new OriginalMetadataItem("OME.Image.PhysicalSizeY", "PhysicalSizeY", String.valueOf(standardMeta.physicalSizeY()), "SPATIAL", vendorName));
            list.add(new OriginalMetadataItem("OME.Image.PhysicalSizeZ", "PhysicalSizeZ", String.valueOf(standardMeta.physicalSizeZ()), "SPATIAL", vendorName));

            if (standardMeta.channels() != null) {
                for (int c = 0; c < standardMeta.channels().size(); c++) {
                    ImageMetadata.ChannelInfo ch = standardMeta.channels().get(c);
                    list.add(new OriginalMetadataItem("OME.Image.Channel[" + c + "].Name", "ChannelName", ch.name(), "CHANNEL", vendorName));
                    list.add(new OriginalMetadataItem("OME.Image.Channel[" + c + "].Color", "ChannelColor", ch.hexColor(), "CHANNEL", vendorName));
                }
            }
        }

        // Global Vendor Tags
        if (vendorMeta != null && vendorMeta.globalTags() != null) {
            for (Map.Entry<String, String> entry : vendorMeta.globalTags().entrySet()) {
                list.add(new OriginalMetadataItem("Global." + entry.getKey(), entry.getKey(), entry.getValue(), "VENDOR_GLOBAL", vendorName));
            }
        }

        // Series Vendor Tags
        if (vendorMeta != null && vendorMeta.seriesTags() != null) {
            for (Map.Entry<String, Map<String, String>> seriesEntry : vendorMeta.seriesTags().entrySet()) {
                String seriesName = seriesEntry.getKey();
                for (Map.Entry<String, String> tagEntry : seriesEntry.getValue().entrySet()) {
                    list.add(new OriginalMetadataItem(seriesName + "." + tagEntry.getKey(), tagEntry.getKey(), tagEntry.getValue(), "VENDOR_SERIES", vendorName));
                }
            }
        }

        // Raw XML Header node
        if (vendorMeta != null && vendorMeta.rawXmlHeader() != null && !vendorMeta.rawXmlHeader().isBlank()) {
            list.add(new OriginalMetadataItem("OME.Header.XML", "OMEXMLHeader", "XML_STORED (" + vendorMeta.rawXmlHeader().length() + " bytes)", "TRANSITIONAL_XML", vendorName));
        }

        return list;
    }
}
