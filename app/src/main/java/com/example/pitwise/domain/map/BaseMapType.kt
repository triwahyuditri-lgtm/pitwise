package com.example.pitwise.domain.map

/**
 * Supported base map tile types using Esri ArcGIS Online services.
 *
 * Tile URL pattern:
 *   https://services.arcgisonline.com/ArcGIS/rest/services/{serviceName}/MapServer/tile/{z}/{y}/{x}
 */
enum class BaseMapType(
    val serviceName: String,
    val label: String,
    val labelOverlay: Boolean = false
) {
    SATELLITE("World_Imagery", "Satelit"),
    STREET("World_Street_Map", "Jalan"),
    TOPO("World_Topo_Map", "Topografi"),
    HYBRID("World_Imagery", "Hybrid", labelOverlay = true);

    /** Labels overlay service (used for HYBRID mode). */
    companion object {
        const val LABELS_SERVICE = "Reference/World_Boundaries_and_Places"
    }
}
