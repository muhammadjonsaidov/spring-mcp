package io.salesdoctor.spring_mcp.domain;

/**
 * Ombor joylari (stock.warehouse_location). Asosiy ombor qatorida agent_id NULL bo'ladi,
 * agent mashinasi qatorida esa agent ID si bilan "VAN-{id}".
 */
public enum StockLocation {

    MAIN("MAIN"),
    VAN("VAN-");

    private final String code;

    StockLocation(String code) {
        this.code = code;
    }

    /**
     * Asosiy ombor kodi.
     */
    public static String main() {
        return MAIN.code;
    }

    /**
     * Agent mashinasi kodi, masalan "VAN-3".
     */
    public static String van(Long agentId) {
        return VAN.code + agentId;
    }
}
