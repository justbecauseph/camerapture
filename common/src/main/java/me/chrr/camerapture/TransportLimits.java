package me.chrr.camerapture;

/// Constants defining network transport payload limits for partial picture packet chunks.
public final class TransportLimits {
    private TransportLimits() {
    }

    /// Maximum section size for client-to-server picture uploads (30 KB).
    public static final int CLIENT_SECTION_SIZE = 30_000;

    /// Maximum section size for server-to-client picture downloads (1 MB).
    public static final int SERVER_SECTION_SIZE = 1_000_000;
}
