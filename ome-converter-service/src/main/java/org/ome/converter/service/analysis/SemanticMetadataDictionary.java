package org.ome.converter.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public class SemanticMetadataDictionary {
    private static final Logger log = LoggerFactory.getLogger(SemanticMetadataDictionary.class);
    private static final Map<String, List<String>> STANDARD_CONCEPTS = new LinkedHashMap<>();
    private static final Map<String, String> ALIAS_TO_CONCEPT = new LinkedHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        registerConcept("pixel_size_x", List.of("ResX", "PixelWidth", "Scaling/Distance/Value#X", "VoxelSizeX", "ScaleX", "dCalibration", "PhysicalSizeX", "PixelSizeX"));
        registerConcept("pixel_size_y", List.of("ResY", "PixelHeight", "Scaling/Distance/Value#Y", "VoxelSizeY", "ScaleY", "dCalibration", "PhysicalSizeY", "PixelSizeY"));
        registerConcept("pixel_size_z", List.of("ResZ", "PixelDepth", "Scaling/Distance/Value#Z", "VoxelSizeZ", "ScaleZ", "dZStep", "PhysicalSizeZ", "PixelSizeZ"));
        
        registerConcept("objective_magnification", List.of("ObjectiveMag", "Lens", "Objective/NominalMagnification", "ObjectiveMagnification", "sObjective", "NominalMagnification"));
        registerConcept("numerical_aperture", List.of("NA", "LensNA", "Objective/LensNA", "NumericalAperture", "dLensNA", "LensNumericalAperture", "ObjectiveNA"));
        registerConcept("exposure_time", List.of("ExpTime_ms", "AcquisitionBlock/ExposureTime", "ExposureTime", "dExposureTime", "CameraExposure", "Plane.ExposureTime", "Exposure"));
        registerConcept("excitation_wavelength", List.of("Wavelength", "ExWavelength", "Dimensions/Channels/Channel/ExWavelength", "dWavelength", "ExcitationWavelength", "LaserWavelength"));
        registerConcept("emission_wavelength", List.of("EmWavelength", "Dimensions/Channels/Channel/EmWavelength", "EmissionWavelength", "dEmWavelength"));
        registerConcept("detector_gain", List.of("PMTGain", "Gain", "Detectors/Detector/Gain", "MasterGain", "dGain", "SensorGain", "DetectorGain"));
        registerConcept("laser_power", List.of("LaserPower", "Power", "Laser/Power"));
        registerConcept("immersion_refractive_index", List.of("ImmersionRI", "Medium/RefractiveIndex", "RefractiveIndex", "dRefractiveIndex", "Immersion"));
        registerConcept("stage_position_x", List.of("StageX", "Stage/Position/X", "PosX", "dXPos", "PositionX"));
        registerConcept("stage_position_y", List.of("StageY", "Stage/Position/Y", "PosY", "dYPos", "PositionY"));
        registerConcept("image_width", List.of("Width", "SizeX", "ImageWidth", "ResXPixels"));
        registerConcept("image_height", List.of("Height", "SizeY", "ImageHeight", "ResYPixels"));
        registerConcept("channel_name", List.of("ChannelName", "Name", "ChannelLabel", "Label", "Channel color", "Channel end wavelength"));

        loadDictionaryResource("/dictionary/universal_mapping_dictionary.json");
        loadDictionaryResource("/dictionary/oir_to_ome_mapping.json");
        loadDictionaryResource("/dictionary/final_validator_mapping.json");
        loadDictionaryResource("/dictionary/fixed_validator_mapping.json");
    }

    private static void loadDictionaryResource(String path) {
        try (InputStream is = SemanticMetadataDictionary.class.getResourceAsStream(path)) {
            if (is != null) {
                JsonNode root = mapper.readTree(is);
                log.info("Loaded semantic dictionary mapping file from {}", path);
            }
        } catch (Exception e) {
            log.warn("Could not load dictionary resource: {}", path, e);
        }
    }

    private static void registerConcept(String conceptName, List<String> aliases) {
        STANDARD_CONCEPTS.put(conceptName, aliases);
        for (String alias : aliases) {
            ALIAS_TO_CONCEPT.put(alias.toLowerCase(), conceptName);
        }
        ALIAS_TO_CONCEPT.put(conceptName.toLowerCase(), conceptName);
    }

    public static Optional<String> findCanonicalConcept(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        String normalized = rawKey.trim().toLowerCase();

        if (ALIAS_TO_CONCEPT.containsKey(normalized)) {
            return Optional.of(ALIAS_TO_CONCEPT.get(normalized));
        }

        for (Map.Entry<String, String> entry : ALIAS_TO_CONCEPT.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public static Map<String, List<String>> getStandardConcepts() {
        return Collections.unmodifiableMap(STANDARD_CONCEPTS);
    }
}
