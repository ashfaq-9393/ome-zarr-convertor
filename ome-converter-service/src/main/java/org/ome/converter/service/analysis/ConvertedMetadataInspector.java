package org.ome.converter.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ome.converter.core.model.ConvertedMetadataItem;
import org.ome.converter.core.model.OmeZarrVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ConvertedMetadataInspector {
    private static final Logger log = LoggerFactory.getLogger(ConvertedMetadataInspector.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public List<ConvertedMetadataItem> inspectConvertedDataset(Path zarrRoot, OmeZarrVersion version) {
        List<ConvertedMetadataItem> list = new ArrayList<>();
        String specVerName = version != null ? version.getDisplayName() : "OME-Zarr 0.5";

        try {
            if (version == OmeZarrVersion.OME_ZARR_0_4) {
                inspectZarrV2Dataset(zarrRoot, specVerName, list);
            } else {
                inspectZarrV3Dataset(zarrRoot, specVerName, list);
            }

            // Inspect companion OME/METADATA.ome.xml if present
            Path companionFile = zarrRoot.resolve("OME").resolve("METADATA.ome.xml");
            if (Files.exists(companionFile)) {
                long size = Files.size(companionFile);
                list.add(new ConvertedMetadataItem("OME/METADATA.ome.xml", "METADATA.ome.xml", "XML_FILE (" + size + " bytes)", "TRANSITIONAL_XML", specVerName));
            }

        } catch (Exception e) {
            log.warn("Error inspecting converted dataset at {}: {}", zarrRoot, e.getMessage(), e);
        }

        return list;
    }

    private void inspectZarrV3Dataset(Path zarrRoot, String specVerName, List<ConvertedMetadataItem> list) throws Exception {
        File zarrJson = zarrRoot.resolve("zarr.json").toFile();
        if (!zarrJson.exists()) return;

        JsonNode rootNode = mapper.readTree(zarrJson);
        list.add(new ConvertedMetadataItem("zarr.json:zarr_format", "zarr_format", rootNode.path("zarr_format").asText("3"), "OME_NGFF_CORE", specVerName));
        list.add(new ConvertedMetadataItem("zarr.json:node_type", "node_type", rootNode.path("node_type").asText("group"), "OME_NGFF_CORE", specVerName));

        JsonNode attributes = rootNode.path("attributes");
        if (attributes.isObject()) {
            JsonNode omeNode = attributes.path("ome");
            parseNgffAttributes(omeNode, "zarr.json:attributes.ome", specVerName, list);

            JsonNode vendorNode = attributes.path("vendor_custom_metadata");
            parseVendorCustomAttributes(vendorNode, "zarr.json:attributes.vendor_custom_metadata", specVerName, list);
        }

        // Level 0 array metadata inspection
        File level0Json = zarrRoot.resolve("0").resolve("zarr.json").toFile();
        if (level0Json.exists()) {
            JsonNode level0 = mapper.readTree(level0Json);
            list.add(new ConvertedMetadataItem("0/zarr.json:shape", "shape", level0.path("shape").toString(), "ZARR_ARRAY", specVerName));
            list.add(new ConvertedMetadataItem("0/zarr.json:data_type", "data_type", level0.path("data_type").asText(), "ZARR_ARRAY", specVerName));
        }
    }

    private void inspectZarrV2Dataset(Path zarrRoot, String specVerName, List<ConvertedMetadataItem> list) throws Exception {
        File zgroup = zarrRoot.resolve(".zgroup").toFile();
        if (zgroup.exists()) {
            JsonNode zgNode = mapper.readTree(zgroup);
            list.add(new ConvertedMetadataItem(".zgroup:zarr_format", "zarr_format", zgNode.path("zarr_format").asText("2"), "OME_NGFF_CORE", specVerName));
        }

        File zattrs = zarrRoot.resolve(".zattrs").toFile();
        if (zattrs.exists()) {
            JsonNode attrsNode = mapper.readTree(zattrs);
            parseNgffAttributes(attrsNode, ".zattrs", specVerName, list);

            JsonNode vendorNode = attrsNode.path("vendor_custom_metadata");
            parseVendorCustomAttributes(vendorNode, ".zattrs:vendor_custom_metadata", specVerName, list);
        }

        // Level 0 array metadata inspection
        File level0Zarray = zarrRoot.resolve("0").resolve(".zarray").toFile();
        if (level0Zarray.exists()) {
            JsonNode level0 = mapper.readTree(level0Zarray);
            list.add(new ConvertedMetadataItem("0/.zarray:shape", "shape", level0.path("shape").toString(), "ZARR_ARRAY", specVerName));
            list.add(new ConvertedMetadataItem("0/.zarray:dtype", "dtype", level0.path("dtype").asText(), "ZARR_ARRAY", specVerName));
        }
    }

    private void parseNgffAttributes(JsonNode omeNode, String prefix, String specVerName, List<ConvertedMetadataItem> list) {
        if (omeNode.isMissingNode() || omeNode.isNull()) return;

        JsonNode multiscales = omeNode.path("multiscales");
        if (multiscales.isArray() && !multiscales.isEmpty()) {
            JsonNode ms0 = multiscales.get(0);
            list.add(new ConvertedMetadataItem(prefix + ".multiscales[0].name", "multiscales.name", ms0.path("name").asText(), "OME_NGFF_CORE", specVerName));
            list.add(new ConvertedMetadataItem(prefix + ".multiscales[0].version", "multiscales.version", ms0.path("version").asText(), "OME_NGFF_CORE", specVerName));

            JsonNode datasets = ms0.path("datasets");
            if (datasets.isArray()) {
                for (int i = 0; i < datasets.size(); i++) {
                    JsonNode ds = datasets.get(i);
                    JsonNode transforms = ds.path("coordinateTransformations");
                    if (transforms.isArray() && !transforms.isEmpty()) {
                        JsonNode scale = transforms.get(0).path("scale");
                        if (scale.isArray()) {
                            list.add(new ConvertedMetadataItem(prefix + ".multiscales[0].datasets[" + i + "].scale", "scale", scale.toString(), "OME_NGFF_CORE", specVerName));
                        }
                    }
                }
            }
        }

        JsonNode omero = omeNode.path("omero");
        if (!omero.isMissingNode()) {
            JsonNode channels = omero.path("channels");
            if (channels.isArray()) {
                for (int c = 0; c < channels.size(); c++) {
                    JsonNode ch = channels.get(c);
                    list.add(new ConvertedMetadataItem(prefix + ".omero.channels[" + c + "].label", "ChannelLabel", ch.path("label").asText(), "OMERO", specVerName));
                    list.add(new ConvertedMetadataItem(prefix + ".omero.channels[" + c + "].color", "ChannelColor", ch.path("color").asText(), "OMERO", specVerName));
                }
            }
        }
    }

    private void parseVendorCustomAttributes(JsonNode vendorNode, String prefix, String specVerName, List<ConvertedMetadataItem> list) {
        if (vendorNode.isMissingNode() || vendorNode.isNull()) return;

        JsonNode globalTags = vendorNode.path("vsi_global_tags");
        if (globalTags.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = globalTags.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                list.add(new ConvertedMetadataItem(prefix + ".vsi_global_tags[" + entry.getKey() + "]", entry.getKey(), entry.getValue().asText(), "VENDOR_CUSTOM", specVerName));
            }
        }

        JsonNode seriesTags = vendorNode.path("vsi_series_tags");
        if (seriesTags.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> seriesFields = seriesTags.fields();
            while (seriesFields.hasNext()) {
                Map.Entry<String, JsonNode> sEntry = seriesFields.next();
                String seriesName = sEntry.getKey();
                if (sEntry.getValue().isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> tagFields = sEntry.getValue().fields();
                    while (tagFields.hasNext()) {
                        Map.Entry<String, JsonNode> tEntry = tagFields.next();
                        list.add(new ConvertedMetadataItem(prefix + "." + seriesName + "[" + tEntry.getKey() + "]", tEntry.getKey(), tEntry.getValue().asText(), "VENDOR_CUSTOM", specVerName));
                    }
                }
            }
        }
    }
}
