package com.example.pitwise.domain.dxf

import android.graphics.Color

/**
 * Resolves DXF colors based on ACI (AutoCAD Color Index) and True Color (RGB).
 *
 * Complete 256-color AutoCAD ACI mapping for accurate Minescape rendering.
 */
object DxfColorResolver {

    /**
     * Resolves the color of an entity.
     * Hierarchy:
     * 1. True Color (32-bit integer, group 420)
     * 2. Entity ACI (group 62)
     * 3. Layer Color (ACI)
     */
    fun resolveColor(
        entityTrueColor: Int?,
        entityColorIndex: Int?,
        layerColorIndex: Int
    ): Int {
        // 1. True Color (Group 420)
        if (entityTrueColor != null) {
            return parseTrueColor(entityTrueColor)
        }

        // 2. Entity ACI (Group 62)
        if (entityColorIndex != null) {
            if (entityColorIndex == 0) {
                // ByBlock -> Use Layer Color
                return getColorFromIndex(layerColorIndex)
            }
            if (entityColorIndex == 256) {
                // ByLayer -> Use Layer Color
                return getColorFromIndex(layerColorIndex)
            }
            if (entityColorIndex < 0) {
                // Layer off / invisible
                return Color.TRANSPARENT
            }
            return getColorFromIndex(entityColorIndex)
        }

        // 3. Fallback to Layer Color
        return getColorFromIndex(layerColorIndex)
    }

    private fun parseTrueColor(value: Int): Int {
        val red = (value shr 16) and 0xFF
        val green = (value shr 8) and 0xFF
        val blue = value and 0xFF
        return Color.rgb(red, green, blue) or (0xFF shl 24).toInt()
    }

    fun getColorFromIndex(index: Int): Int {
        if (index < 0 || index > 255) return Color.WHITE
        return ACI_TABLE[index]
    }

    /**
     * Complete AutoCAD Color Index (ACI) table — 256 colors.
     * Standard indices 1–9 are the fixed AutoCAD colors.
     * Indices 10–249 follow the HSV-based AutoCAD spectral pattern.
     * Indices 250–255 are grayscale shades.
     */
    private val ACI_TABLE: IntArray = intArrayOf(
        // 0: ByBlock (black placeholder)
        Color.BLACK,
        // 1–9: Standard AutoCAD colors
        Color.RED,                          // 1  Red
        Color.YELLOW,                       // 2  Yellow
        Color.GREEN,                        // 3  Green
        Color.CYAN,                         // 4  Cyan
        Color.BLUE,                         // 5  Blue
        Color.MAGENTA,                      // 6  Magenta
        Color.WHITE,                        // 7  White
        Color.DKGRAY,                       // 8  Dark Gray
        Color.LTGRAY,                       // 9  Light Gray
        // 10–19: Red row
        rgb(255, 0, 0),     // 10
        rgb(255, 127, 127), // 11
        rgb(204, 0, 0),     // 12
        rgb(204, 102, 102), // 13
        rgb(153, 0, 0),     // 14
        rgb(153, 76, 76),   // 15
        rgb(127, 0, 0),     // 16
        rgb(127, 63, 63),   // 17
        rgb(76, 0, 0),      // 18
        rgb(76, 38, 38),    // 19
        // 20–29: Orange-Red
        rgb(255, 63, 0),    // 20
        rgb(255, 159, 127), // 21
        rgb(204, 51, 0),    // 22
        rgb(204, 127, 102), // 23
        rgb(153, 38, 0),    // 24
        rgb(153, 95, 76),   // 25
        rgb(127, 31, 0),    // 26
        rgb(127, 79, 63),   // 27
        rgb(76, 19, 0),     // 28
        rgb(76, 47, 38),    // 29
        // 30–39: Orange
        rgb(255, 127, 0),   // 30
        rgb(255, 191, 127), // 31
        rgb(204, 102, 0),   // 32
        rgb(204, 153, 102), // 33
        rgb(153, 76, 0),    // 34
        rgb(153, 114, 76),  // 35
        rgb(127, 63, 0),    // 36
        rgb(127, 95, 63),   // 37
        rgb(76, 38, 0),     // 38
        rgb(76, 57, 38),    // 39
        // 40–49: Gold
        rgb(255, 191, 0),   // 40
        rgb(255, 223, 127), // 41
        rgb(204, 153, 0),   // 42
        rgb(204, 178, 102), // 43
        rgb(153, 114, 0),   // 44
        rgb(153, 133, 76),  // 45
        rgb(127, 95, 0),    // 46
        rgb(127, 111, 63),  // 47
        rgb(76, 57, 0),     // 48
        rgb(76, 66, 38),    // 49
        // 50–59: Yellow
        rgb(255, 255, 0),   // 50
        rgb(255, 255, 127), // 51
        rgb(204, 204, 0),   // 52
        rgb(204, 204, 102), // 53
        rgb(153, 153, 0),   // 54
        rgb(153, 153, 76),  // 55
        rgb(127, 127, 0),   // 56
        rgb(127, 127, 63),  // 57
        rgb(76, 76, 0),     // 58
        rgb(76, 76, 38),    // 59
        // 60–69: Yellow-Green
        rgb(191, 255, 0),   // 60
        rgb(223, 255, 127), // 61
        rgb(153, 204, 0),   // 62
        rgb(178, 204, 102), // 63
        rgb(114, 153, 0),   // 64
        rgb(133, 153, 76),  // 65
        rgb(95, 127, 0),    // 66
        rgb(111, 127, 63),  // 67
        rgb(57, 76, 0),     // 68
        rgb(66, 76, 38),    // 69
        // 70–79: Chartreuse
        rgb(127, 255, 0),   // 70
        rgb(191, 255, 127), // 71
        rgb(102, 204, 0),   // 72
        rgb(153, 204, 102), // 73
        rgb(76, 153, 0),    // 74
        rgb(114, 153, 76),  // 75
        rgb(63, 127, 0),    // 76
        rgb(95, 127, 63),   // 77
        rgb(38, 76, 0),     // 78
        rgb(57, 76, 38),    // 79
        // 80–89: Green-Yellow
        rgb(63, 255, 0),    // 80
        rgb(159, 255, 127), // 81
        rgb(51, 204, 0),    // 82
        rgb(127, 204, 102), // 83
        rgb(38, 153, 0),    // 84
        rgb(95, 153, 76),   // 85
        rgb(31, 127, 0),    // 86
        rgb(79, 127, 63),   // 87
        rgb(19, 76, 0),     // 88
        rgb(47, 76, 38),    // 89
        // 90–99: Green
        rgb(0, 255, 0),     // 90
        rgb(127, 255, 127), // 91
        rgb(0, 204, 0),     // 92
        rgb(102, 204, 102), // 93
        rgb(0, 153, 0),     // 94
        rgb(76, 153, 76),   // 95
        rgb(0, 127, 0),     // 96
        rgb(63, 127, 63),   // 97
        rgb(0, 76, 0),      // 98
        rgb(38, 76, 38),    // 99
        // 100–109: Green-Cyan
        rgb(0, 255, 63),    // 100
        rgb(127, 255, 159), // 101
        rgb(0, 204, 51),    // 102
        rgb(102, 204, 127), // 103
        rgb(0, 153, 38),    // 104
        rgb(76, 153, 95),   // 105
        rgb(0, 127, 31),    // 106
        rgb(63, 127, 79),   // 107
        rgb(0, 76, 19),     // 108
        rgb(38, 76, 47),    // 109
        // 110–119: Cyan-Green
        rgb(0, 255, 127),   // 110
        rgb(127, 255, 191), // 111
        rgb(0, 204, 102),   // 112
        rgb(102, 204, 153), // 113
        rgb(0, 153, 76),    // 114
        rgb(76, 153, 114),  // 115
        rgb(0, 127, 63),    // 116
        rgb(63, 127, 95),   // 117
        rgb(0, 76, 38),     // 118
        rgb(38, 76, 57),    // 119
        // 120–129: Spring Green
        rgb(0, 255, 191),   // 120
        rgb(127, 255, 223), // 121
        rgb(0, 204, 153),   // 122
        rgb(102, 204, 178), // 123
        rgb(0, 153, 114),   // 124
        rgb(76, 153, 133),  // 125
        rgb(0, 127, 95),    // 126
        rgb(63, 127, 111),  // 127
        rgb(0, 76, 57),     // 128
        rgb(38, 76, 66),    // 129
        // 130–139: Cyan
        rgb(0, 255, 255),   // 130
        rgb(127, 255, 255), // 131
        rgb(0, 204, 204),   // 132
        rgb(102, 204, 204), // 133
        rgb(0, 153, 153),   // 134
        rgb(76, 153, 153),  // 135
        rgb(0, 127, 127),   // 136
        rgb(63, 127, 127),  // 137
        rgb(0, 76, 76),     // 138
        rgb(38, 76, 76),    // 139
        // 140–149: Cyan-Blue
        rgb(0, 191, 255),   // 140
        rgb(127, 223, 255), // 141
        rgb(0, 153, 204),   // 142
        rgb(102, 178, 204), // 143
        rgb(0, 114, 153),   // 144
        rgb(76, 133, 153),  // 145
        rgb(0, 95, 127),    // 146
        rgb(63, 111, 127),  // 147
        rgb(0, 57, 76),     // 148
        rgb(38, 66, 76),    // 149
        // 150–159: Azure
        rgb(0, 127, 255),   // 150
        rgb(127, 191, 255), // 151
        rgb(0, 102, 204),   // 152
        rgb(102, 153, 204), // 153
        rgb(0, 76, 153),    // 154
        rgb(76, 114, 153),  // 155
        rgb(0, 63, 127),    // 156
        rgb(63, 95, 127),   // 157
        rgb(0, 38, 76),     // 158
        rgb(38, 57, 76),    // 159
        // 160–169: Blue-Azure
        rgb(0, 63, 255),    // 160
        rgb(127, 159, 255), // 161
        rgb(0, 51, 204),    // 162
        rgb(102, 127, 204), // 163
        rgb(0, 38, 153),    // 164
        rgb(76, 95, 153),   // 165
        rgb(0, 31, 127),    // 166
        rgb(63, 79, 127),   // 167
        rgb(0, 19, 76),     // 168
        rgb(38, 47, 76),    // 169
        // 170–179: Blue
        rgb(0, 0, 255),     // 170
        rgb(127, 127, 255), // 171
        rgb(0, 0, 204),     // 172
        rgb(102, 102, 204), // 173
        rgb(0, 0, 153),     // 174
        rgb(76, 76, 153),   // 175
        rgb(0, 0, 127),     // 176
        rgb(63, 63, 127),   // 177
        rgb(0, 0, 76),      // 178
        rgb(38, 38, 76),    // 179
        // 180–189: Blue-Violet
        rgb(63, 0, 255),    // 180
        rgb(159, 127, 255), // 181
        rgb(51, 0, 204),    // 182
        rgb(127, 102, 204), // 183
        rgb(38, 0, 153),    // 184
        rgb(95, 76, 153),   // 185
        rgb(31, 0, 127),    // 186
        rgb(79, 63, 127),   // 187
        rgb(19, 0, 76),     // 188
        rgb(47, 38, 76),    // 189
        // 190–199: Violet
        rgb(127, 0, 255),   // 190
        rgb(191, 127, 255), // 191
        rgb(102, 0, 204),   // 192
        rgb(153, 102, 204), // 193
        rgb(76, 0, 153),    // 194
        rgb(114, 76, 153),  // 195
        rgb(63, 0, 127),    // 196
        rgb(95, 63, 127),   // 197
        rgb(38, 0, 76),     // 198
        rgb(57, 38, 76),    // 199
        // 200–209: Purple
        rgb(191, 0, 255),   // 200
        rgb(223, 127, 255), // 201
        rgb(153, 0, 204),   // 202
        rgb(178, 102, 204), // 203
        rgb(114, 0, 153),   // 204
        rgb(133, 76, 153),  // 205
        rgb(95, 0, 127),    // 206
        rgb(111, 63, 127),  // 207
        rgb(57, 0, 76),     // 208
        rgb(66, 38, 76),    // 209
        // 210–219: Magenta
        rgb(255, 0, 255),   // 210
        rgb(255, 127, 255), // 211
        rgb(204, 0, 204),   // 212
        rgb(204, 102, 204), // 213
        rgb(153, 0, 153),   // 214
        rgb(153, 76, 153),  // 215
        rgb(127, 0, 127),   // 216
        rgb(127, 63, 127),  // 217
        rgb(76, 0, 76),     // 218
        rgb(76, 38, 76),    // 219
        // 220–229: Magenta-Rose
        rgb(255, 0, 191),   // 220
        rgb(255, 127, 223), // 221
        rgb(204, 0, 153),   // 222
        rgb(204, 102, 178), // 223
        rgb(153, 0, 114),   // 224
        rgb(153, 76, 133),  // 225
        rgb(127, 0, 95),    // 226
        rgb(127, 63, 111),  // 227
        rgb(76, 0, 57),     // 228
        rgb(76, 38, 66),    // 229
        // 230–239: Rose
        rgb(255, 0, 127),   // 230
        rgb(255, 127, 191), // 231
        rgb(204, 0, 102),   // 232
        rgb(204, 102, 153), // 233
        rgb(153, 0, 76),    // 234
        rgb(153, 76, 114),  // 235
        rgb(127, 0, 63),    // 236
        rgb(127, 63, 95),   // 237
        rgb(76, 0, 38),     // 238
        rgb(76, 38, 57),    // 239
        // 240–249: Rose-Red
        rgb(255, 0, 63),    // 240
        rgb(255, 127, 159), // 241
        rgb(204, 0, 51),    // 242
        rgb(204, 102, 127), // 243
        rgb(153, 0, 38),    // 244
        rgb(153, 76, 95),   // 245
        rgb(127, 0, 31),    // 246
        rgb(127, 63, 79),   // 247
        rgb(76, 0, 19),     // 248
        rgb(76, 38, 47),    // 249
        // 250–255: Grayscale
        rgb(51, 51, 51),    // 250
        rgb(91, 91, 91),    // 251
        rgb(132, 132, 132), // 252
        rgb(173, 173, 173), // 253
        rgb(214, 214, 214), // 254
        rgb(255, 255, 255)  // 255
    )

    /** Helper to create opaque ARGB int from RGB. */
    private fun rgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
