package com.unciv.ui.nativeui

import com.badlogic.gdx.graphics.Color
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 * Native minimap. It reads the Scene2D [com.unciv.ui.screens.worldscreen.minimap.Minimap], which
 * keeps running hidden, so explored region, world wrap and the camera rectangle stay exactly as
 * the game computes them; only the drawing moves to the platform.
 *
 * Tiles change rarely and are sent with world updates; the camera moves every frame while
 * panning and is polled separately through [cameraIfChanged].
 */
class MinimapPresenter(private val worldScreen: WorldScreen) {
    private var lastTilesJson = ""
    private val lastCamera = FloatArray(4) { -1f }
    private val camera = FloatArray(4)

    /** Forget what was sent, so the next calls resend everything (e.g. after the overlay was hidden) */
    fun invalidate() {
        lastTilesJson = ""
        lastCamera.fill(-1f)
    }

    /** @return the tiles snapshot when it differs from the last one returned, otherwise null */
    fun tilesJsonIfChanged(): String? {
        val json = tilesJson()
        if (json == lastTilesJson) return null
        lastTilesJson = json
        return json
    }

    private fun tilesJson(): String {
        val settings = UncivGame.Current.settings
        val minimap = worldScreen.minimapWrapper.minimapOrNull
        return NativeUiJson().obj {
            put("visible", settings.showMinimap && minimap != null)
            obj("layers") {
                put("movement", settings.showUnitMovements)
                put("yields", settings.showTileYields)
                put("worked", settings.showWorkedTiles)
                put("resources", settings.showResourcesAndImprovements)
                put("improvements", settings.showPixelImprovements)
            }
            if (minimap == null || !settings.showMinimap) return@obj

            val palette = LinkedHashMap<String, Int>()
            fun colorIndex(color: Color) = palette.getOrPut("#" + color.toString().take(6)) { palette.size }

            val tiles = minimap.revealedTiles.toList()
            val tileValues = FloatArray(tiles.size * 3)
            val cityValues = ArrayList<Float>()
            for ((i, minimapTile) in tiles.withIndex()) {
                val image = minimapTile.image
                val centerX = image.x + image.width / 2
                val centerY = image.y + image.height / 2
                tileValues[i * 3] = centerX
                tileValues[i * 3 + 1] = centerY
                tileValues[i * 3 + 2] = colorIndex(image.color).toFloat()

                val tile = minimapTile.tile
                if (!tile.isCityCenter()) continue
                val civ = tile.getOwner() ?: continue
                val isCapital = tile.getCity()?.isCapital() == true && civ.isMajorCiv()
                cityValues += centerX
                cityValues += centerY
                cityValues += colorIndex(civ.nation.getOuterColor()).toFloat()
                cityValues += colorIndex(civ.nation.getInnerColor()).toFloat()
                cityValues += if (isCapital) 1f else 0f
            }
            put("width", minimap.width)
            put("height", minimap.height)
            put("tileSize", tiles.firstOrNull()?.image?.width ?: 0f)
            strings("palette", palette.keys)
            numbers("tiles", tileValues)
            numbers("cities", cityValues.toFloatArray())
        }.toString()
    }

    /** @return the camera rectangle as fractions of the minimap (x, y from the bottom left, width, height) when it moved */
    fun cameraIfChanged(): FloatArray? {
        val minimap = worldScreen.minimapWrapper.minimapOrNull ?: return null
        if (minimap.width <= 0f || minimap.height <= 0f) return null
        val rect = minimap.cameraRectangle
        camera[0] = rect.x / minimap.width
        camera[1] = rect.y / minimap.height
        camera[2] = rect.width / minimap.width
        camera[3] = rect.height / minimap.height
        if (camera.contentEquals(lastCamera)) return null
        camera.copyInto(lastCamera)
        return camera
    }

    /** `tap:<x>:<y>` with fractions of the minimap from the bottom left, or `layer:<name>` */
    fun perform(id: String) {
        val parts = id.split(':')
        when (parts[0]) {
            "tap" -> {
                val minimap = worldScreen.minimapWrapper.minimapOrNull ?: return
                val x = parts.getOrNull(1)?.toFloatOrNull() ?: return
                val y = parts.getOrNull(2)?.toFloatOrNull() ?: return
                val tile = minimap.tileNear(x * minimap.width, y * minimap.height) ?: return
                worldScreen.mapHolder.setCenterPosition(tile.position, immediately = true, selectUnit = false)
            }
            "layer" -> {
                val settings = UncivGame.Current.settings
                when (parts.getOrNull(1)) {
                    "movement" -> settings.showUnitMovements = !settings.showUnitMovements
                    "yields" -> settings.showTileYields = !settings.showTileYields
                    "worked" -> settings.showWorkedTiles = !settings.showWorkedTiles
                    "resources" -> settings.showResourcesAndImprovements = !settings.showResourcesAndImprovements
                    "improvements" -> settings.showPixelImprovements = !settings.showPixelImprovements
                    else -> return
                }
                GUI.setUpdateWorldOnNextRender()
            }
        }
    }
}
