package org.ome.converter.core.api;

import org.ome.converter.core.exception.MetadataException;
import org.ome.converter.core.model.VendorMetadata;

import java.io.File;

public interface VendorMetadataDumper {
    VendorMetadata extractVendorMetadata(File sourceFile) throws MetadataException;
}
