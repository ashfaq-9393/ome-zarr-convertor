package org.ome.converter.plugin.vsi;

import org.junit.jupiter.api.Test;
import org.ome.converter.core.model.ImageMetadata;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OmeNgffMetadataBuilderTest {

    @Test
    void testBuildOmeMetadataStructure() {
        ImageMetadata meta = new ImageMetadata(
            "sample_slide.vsi",
            2048, 2048, 1, 3, 1,
            0.325, 0.325, 1.0,
            "micrometer", "micrometer", "micrometer",
            "uint16", 2, 3,
            List.of(
                new ImageMetadata.ChannelInfo(0, "GFP", "00FF00", 0, 65535),
                new ImageMetadata.ChannelInfo(1, "DAPI", "0000FF", 0, 65535),
                new ImageMetadata.ChannelInfo(2, "TexasRed", "FF0000", 0, 65535)
            )
        );

        OmeNgffMetadataBuilder builder = new OmeNgffMetadataBuilder();
        Map<String, Object> omeMap = builder.buildOmeMetadata(meta);

        assertThat(omeMap).containsEntry("version", "0.5");
        assertThat(omeMap).containsKey("multiscales");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> multiscales = (List<Map<String, Object>>) omeMap.get("multiscales");
        assertThat(multiscales).hasSize(1);

        Map<String, Object> scaleGroup = multiscales.get(0);
        assertThat(scaleGroup.get("name")).isEqualTo("sample_slide.vsi");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) scaleGroup.get("datasets");
        assertThat(datasets).hasSize(3);
        assertThat(datasets.get(0).get("path")).isEqualTo("0");
        assertThat(datasets.get(1).get("path")).isEqualTo("1");
        assertThat(datasets.get(2).get("path")).isEqualTo("2");

        @SuppressWarnings("unchecked")
        Map<String, Object> omero = (Map<String, Object>) omeMap.get("omero");
        assertThat(omero).containsKey("channels");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> channels = (List<Map<String, Object>>) omero.get("channels");
        assertThat(channels).hasSize(3);
        assertThat(channels.get(0).get("label")).isEqualTo("GFP");
    }
}
