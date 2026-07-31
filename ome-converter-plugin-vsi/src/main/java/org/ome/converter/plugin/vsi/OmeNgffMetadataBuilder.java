package org.ome.converter.plugin.vsi;

import org.ome.converter.core.model.ImageMetadata;

import java.util.*;

public class OmeNgffMetadataBuilder {

    public Map<String, Object> buildOmeMetadata(ImageMetadata meta) {
        Map<String, Object> ome = new LinkedHashMap<>();
        ome.put("version", "0.5");

        // Multiscales
        List<Map<String, Object>> multiscales = new ArrayList<>();
        Map<String, Object> scaleGroup = new LinkedHashMap<>();
        scaleGroup.put("name", meta.imageName());
        scaleGroup.put("version", "0.5");
        scaleGroup.put("type", "gaussian");

        // Axes [t, c, z, y, x]
        List<Map<String, String>> axes = new ArrayList<>();
        axes.add(createAxis("t", "time", "second"));
        axes.add(createAxis("c", "channel", null));
        axes.add(createAxis("z", "space", meta.unitZ()));
        axes.add(createAxis("y", "space", meta.unitY()));
        axes.add(createAxis("x", "space", meta.unitX()));
        scaleGroup.put("axes", axes);

        // Datasets pyramid levels
        List<Map<String, Object>> datasets = new ArrayList<>();
        int levels = Math.max(1, meta.pyramidLevels());
        for (int l = 0; l < levels; l++) {
            Map<String, Object> ds = new LinkedHashMap<>();
            ds.put("path", String.valueOf(l));

            double factor = Math.pow(2, l);
            double scaleX = meta.physicalSizeX() * factor;
            double scaleY = meta.physicalSizeY() * factor;
            double scaleZ = meta.physicalSizeZ();

            List<Map<String, Object>> transforms = new ArrayList<>();
            Map<String, Object> scaleTransform = new LinkedHashMap<>();
            scaleTransform.put("type", "scale");
            scaleTransform.put("scale", List.of(1.0, 1.0, scaleZ, scaleY, scaleX));
            transforms.add(scaleTransform);

            ds.put("coordinateTransformations", transforms);
            datasets.add(ds);
        }
        scaleGroup.put("datasets", datasets);
        multiscales.add(scaleGroup);
        ome.put("multiscales", multiscales);

        // OMERO visual rendering metadata
        Map<String, Object> omero = new LinkedHashMap<>();
        List<Map<String, Object>> channels = new ArrayList<>();
        if (meta.channels() != null) {
            for (ImageMetadata.ChannelInfo ch : meta.channels()) {
                Map<String, Object> chMap = new LinkedHashMap<>();
                chMap.put("label", ch.name());
                chMap.put("color", ch.hexColor());
                chMap.put("active", true);
                Map<String, Object> win = new LinkedHashMap<>();
                win.put("min", ch.windowMin());
                win.put("max", ch.windowMax());
                win.put("start", ch.windowMin());
                win.put("end", ch.windowMax());
                chMap.put("window", win);
                channels.add(chMap);
            }
        }
        omero.put("channels", channels);
        ome.put("omero", omero);

        return ome;
    }

    private Map<String, String> createAxis(String name, String type, String unit) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("type", type);
        if (unit != null) {
            map.put("unit", unit);
        }
        return map;
    }
}
