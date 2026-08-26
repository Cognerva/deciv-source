package com.unciv.app.ios;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.unciv.Constants;
import com.unciv.ui.components.fonts.FontFamilyData;
import com.unciv.ui.components.fonts.FontImplementation;
import com.unciv.ui.components.fonts.FontMetricsCommon;
import com.unciv.ui.components.fonts.Fonts;
import kotlin.sequences.Sequence;
import java.util.Collections;
import java.util.Iterator;

/**
 * Uses LibGDX's embedded LSans fallback font for the first iOS slice.
 *
 * Unciv's normal font manager is platform-pluggable, so this keeps every existing
 * Scene2D screen intact while we add native font enumeration in a later pass.
 */
public final class IOSFont implements FontImplementation {
    private int requestedSize = 100;

    @Override
    public void setFontFamily(FontFamilyData fontFamilyData, int size) {
        requestedSize = size;
    }

    @Override
    public int getFontSize() {
        return requestedSize;
    }

    @Override
    public Pixmap getCharPixmap(String symbolString) {
        return new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    }

    @Override
    public Sequence<FontFamilyData> getSystemFonts() {
        final FontFamilyData defaultFont = FontFamilyData.Companion.getDefault();
        return new Sequence<FontFamilyData>() {
            @Override
            public Iterator<FontFamilyData> iterator() {
                return Collections.singleton(defaultFont).iterator();
            }
        };
    }

    @Override
    public BitmapFont getBitmapFont() {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(
            Gdx.files.internal("com/badlogic/gdx/utils/lsans-15.fnt"), false
        );
        Pixmap source = new Pixmap(Gdx.files.internal("com/badlogic/gdx/utils/lsans-15.png"));
        float scale = requestedSize / data.lineHeight;
        Pixmap scaledAtlas = new Pixmap(
            Math.max(1, Math.round(source.getWidth() * scale)),
            Math.max(1, Math.round(source.getHeight() * scale)),
            Pixmap.Format.RGBA8888
        );
        scaledAtlas.drawPixmap(
            source, 0, 0, source.getWidth(), source.getHeight(),
            0, 0, scaledAtlas.getWidth(), scaledAtlas.getHeight()
        );
        source.dispose();
        scaleFontData(data, scale);
        data.setScale(Constants.defaultFontSize / Fonts.ORIGINAL_FONT_SIZE);

        Texture texture = new Texture(scaledAtlas);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        BitmapFont font = new BitmapFont(data, new TextureRegion(texture), false);
        font.setOwnsTexture(true);
        font.getData().markupEnabled = true;
        return font;
    }

    @Override
    public FontMetricsCommon getMetrics() {
        return new FontMetricsCommon(14f, 4f, 18f, 0f);
    }

    private static void scaleFontData(BitmapFont.BitmapFontData data, float scale) {
        data.lineHeight *= scale;
        data.capHeight *= scale;
        data.ascent *= scale;
        data.descent *= scale;
        data.down *= scale;
        data.padTop *= scale;
        data.padRight *= scale;
        data.padBottom *= scale;
        data.padLeft *= scale;
        data.spaceXadvance *= scale;
        data.xHeight *= scale;
        for (Glyph[] row : data.glyphs) {
            if (row == null) continue;
            for (Glyph glyph : row) {
                if (glyph == null) continue;
                glyph.srcX = Math.round(glyph.srcX * scale);
                glyph.srcY = Math.round(glyph.srcY * scale);
                glyph.width = Math.max(1, Math.round(glyph.width * scale));
                glyph.height = Math.max(1, Math.round(glyph.height * scale));
                glyph.xoffset = Math.round(glyph.xoffset * scale);
                glyph.yoffset = Math.round(glyph.yoffset * scale);
                glyph.xadvance = Math.round(glyph.xadvance * scale);
            }
        }
    }
}
