package org.ome.converter.core.model;

public record ChunkSpec(
    int tileWidth,
    int tileHeight,
    int tileDepth,
    Codec codec,
    int compressionLevel
) {
    public enum Codec {
        ZSTD("zstd"),
        GZIP("gzip"),
        RAW("raw"),
        BLOSC("blosc");

        private final String name;

        Codec(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static ChunkSpec defaultSpec() {
        return new ChunkSpec(512, 512, 1, Codec.ZSTD, 3);
    }
}
