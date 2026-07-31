package org.ome.converter.core.engine;

public class TileChunk {
    private final int series;
    private final int level;
    private final int channel;
    private final int zIndex;
    private final int tIndex;
    private final int xIndex;
    private final int yIndex;
    private final int tileX;
    private final int tileY;
    private final int tileWidth;
    private final int tileHeight;
    private final byte[] pixelData;
    private final boolean poisonPill;

    public TileChunk(int series, int level, int channel, int zIndex, int tIndex,
                     int xIndex, int yIndex, int tileX, int tileY,
                     int tileWidth, int tileHeight, byte[] pixelData) {
        this.series = series;
        this.level = level;
        this.channel = channel;
        this.zIndex = zIndex;
        this.tIndex = tIndex;
        this.xIndex = xIndex;
        this.yIndex = yIndex;
        this.tileX = tileX;
        this.tileY = tileY;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.pixelData = pixelData;
        this.poisonPill = false;
    }

    private TileChunk(boolean poisonPill) {
        this.series = -1;
        this.level = -1;
        this.channel = -1;
        this.zIndex = -1;
        this.tIndex = -1;
        this.xIndex = -1;
        this.yIndex = -1;
        this.tileX = -1;
        this.tileY = -1;
        this.tileWidth = -1;
        this.tileHeight = -1;
        this.pixelData = null;
        this.poisonPill = poisonPill;
    }

    public static TileChunk poisonPill() {
        return new TileChunk(true);
    }

    public int getSeries() { return series; }
    public int getLevel() { return level; }
    public int getChannel() { return channel; }
    public int getZIndex() { return zIndex; }
    public int getTIndex() { return tIndex; }
    public int getXIndex() { return xIndex; }
    public int getYIndex() { return yIndex; }
    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }
    public int getTileWidth() { return tileWidth; }
    public int getTileHeight() { return tileHeight; }
    public byte[] getPixelData() { return pixelData; }
    public boolean isPoisonPill() { return poisonPill; }
    public long getByteSize() { return pixelData != null ? pixelData.length : 0; }
}
