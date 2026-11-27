package de.mrjulsen.mcdragonlib.client.ber;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.joml.Vector3f;
import com.mojang.blaze3d.font.GlyphInfo;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.PaddingF;
import de.mrjulsen.mcdragonlib.client.util.DLGraphics;
import de.mrjulsen.mcdragonlib.client.util.FontUtils;
import de.mrjulsen.mcdragonlib.client.util.RenderUtils;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.Pair;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Point;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import de.mrjulsen.mcdragonlib.util.math.Size;
import de.mrjulsen.mcdragonlib.util.properties.BooleanProperty;
import de.mrjulsen.mcdragonlib.util.properties.ColorProperty;
import de.mrjulsen.mcdragonlib.util.properties.NumberProperty;
import de.mrjulsen.mcdragonlib.util.properties.Property;
import de.mrjulsen.mcdragonlib.util.properties.VirtualProperty;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.util.StringDecomposer;

public class BERLabel {

    public enum EScrollMode { NEVER, WHEN_NEEDED, ALWAYS, FLEX_FIT }

    protected static record StyledChar(
        CharData charData,
        Style style,
        float unscaledAdvance
    ) {}

    // NeoForge 26.2 port: 1.21 kept the glyph here to shift its texture coordinates while drawing a
    // clipped character. 26.2 glyphs are immutable and drawn later, so the character quad itself is
    // clipped instead (ClippedGlyphConsumer); only the metrics are cached.
    protected static record CharData(
        int charCode,
        String charString,
        boolean isBold,
        GlyphInfo glyphInfo
    ) {}

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final RandomSource RANDOM = RandomSource.create();

    protected static final Map<Integer, CharData> charDataCache = new ConcurrentHashMap<>();

    public final Property<Rectangle> clippingArea = new Property<>(Rectangle.withSize(0, 0, Short.MAX_VALUE, Short.MAX_VALUE));
    public final NumberProperty<Float> x = new NumberProperty<>(0F);
    public final NumberProperty<Float> y = new NumberProperty<>(0F);
    public final VirtualProperty<Point> position = new VirtualProperty<Point>(Point.of(0, 0),
        () -> Point.of(x.get(), y.get()),
        (p) -> {
            x.set((float)p.x());
            y.set((float)p.y());
        });

    public final Property<ETextAlignment> horizontalAlign = new Property<>(ETextAlignment.LEFT);

    public final Property<Component> text = new Property<>(TextUtils.empty());
    public final ColorProperty color = new ColorProperty(DLColor.WHITE, DLColor.WHITE);
    public final ColorProperty backgroundColor = new ColorProperty(DLColor.TRANSPARENT, DLColor.TRANSPARENT);
    
    public final Property<PaddingF> backgroundPadding = new Property<>(new PaddingF(1, 1, 1, 1));
    public final BooleanProperty fullBackground = new BooleanProperty(false);
    public final BooleanProperty glowing = new BooleanProperty(false);

    public final NumberProperty<Float> preferredWidth = new NumberProperty<>(16F, 0F, (float) Integer.MAX_VALUE);
    public final NumberProperty<Float> preferredHeight = new NumberProperty<>(16F, 0F, (float) Integer.MAX_VALUE);     
    public final VirtualProperty<Size> preferredSize = new VirtualProperty<Size>(Size.of(0F, 0F),
        () -> Size.of(preferredWidth.get(), preferredHeight.get()),
        (p) -> {
            preferredWidth.set((float)p.w());
            preferredHeight.set((float)p.h());
        });             
    public final VirtualProperty<Rectangle> layoutArea = new VirtualProperty<Rectangle>(Rectangle.withSize(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE),
        () -> Rectangle.withSize(x.get(), y.get(), preferredWidth.get(), preferredHeight.get()),
        (r) -> {
            x.set((float)r.x());
            y.set((float)r.y());
            preferredWidth.set((float)r.width());
            preferredHeight.set((float)r.height());
        });

    public final NumberProperty<Float> horizontalMinScale = new NumberProperty<>(1F, 0F, (float) Integer.MAX_VALUE);
    public final NumberProperty<Float> horizontalMaxScale = new NumberProperty<>(1F, 0F, (float) Integer.MAX_VALUE);
    public final VirtualProperty<Pair<Float, Float>> horizontalScale = new VirtualProperty<Pair<Float, Float>>(Pair.of(0F, 0F),
        () -> Pair.of(horizontalMinScale.get(), horizontalMaxScale.get()),
        (p) -> {
            horizontalMinScale.set((float)p.getFirst());
            horizontalMaxScale.set((float)p.getSecond());
        });
    public final NumberProperty<Float> verticalMinScale = new NumberProperty<>(1F, 0F, (float) Integer.MAX_VALUE);
    public final NumberProperty<Float> verticalMaxScale = new NumberProperty<>(1F, 0F, (float) Integer.MAX_VALUE);
    public final VirtualProperty<Pair<Float, Float>> verticalScale = new VirtualProperty<Pair<Float, Float>>(Pair.of(0F, 0F),
        () -> Pair.of(verticalMinScale.get(), verticalMaxScale.get()),
        (p) -> {
            verticalMinScale.set((float)p.getFirst());
            verticalMaxScale.set((float)p.getSecond());
        });

    public final NumberProperty<Float> horizontalScrollingSpeed = new NumberProperty<>(4F);
    public final NumberProperty<Float> verticalScrollingSpeed = new NumberProperty<>(4F);
    public final Property<EScrollMode> horizontalScrollMode = new Property<>(EScrollMode.NEVER);
    public final Property<EScrollMode> verticalScrollMode = new Property<>(EScrollMode.NEVER);

    private final FontUtils fontUtils = new FontUtils(FontDescription.DEFAULT);

    private long lastRenderTime = 0;
    private float horizontalScrollOffset = 0.0f;
    private float verticalScrollOffset = 0.0f;

    private List<StyledChar> cachedGlyphs = null;
    private float cachedUnscaledTextWidth = 0f;
    private Component lastRenderedText = null;
    
    private float cachedFinalHScale = 1.0f;
    private float cachedFinalVScale = 1.0f;

    public BERLabel() {
        charDataCache.clear();
        text.withAfterPropertyChangedCallback((oldVal, newVal) -> invalidateCache());
    }

    private void invalidateCache() {
        cachedGlyphs = null;
        cachedUnscaledTextWidth = 0f;
        lastRenderedText = null;
    }    

    private CharData getCharData(int codePoint, Style style) {
        int key = codePoint;
        if (style.isBold()) key |= (1 << 24); 

        return charDataCache.computeIfAbsent(key, c -> {
            boolean isBold = (c & (1 << 24)) != 0;
            GlyphInfo info = fontUtils.getGlyphInfo(codePoint);
            return new CharData(codePoint, String.valueOf(Character.toChars(codePoint)), isBold, info);
        });
    }

    private void updateGlyphCache(Component component) {
        if (component == null || component.equals(lastRenderedText)) return;

        List<StyledChar> glyphs = new ArrayList<>();
        MutableFloat widthSum = new MutableFloat();

        StringDecomposer.iterateFormatted(component, Style.EMPTY, (charIndex, styl, codePoint) -> {
            CharData data = getCharData(codePoint, styl);
            float adv = data.glyphInfo().getAdvance(styl.isBold());
            glyphs.add(new StyledChar(data, styl, adv));
            widthSum.add(adv);
            return true;
        });

        cachedGlyphs = glyphs;
        cachedUnscaledTextWidth = widthSum.getValue();
        lastRenderedText = component;
    }

    private void calculateFinalScales() {
        final float charQuadSize = 8.0f;
        float targetW = preferredWidth.get();
        float targetH = preferredHeight.get();
        
        float minHScale = Math.max(horizontalMinScale.get(), 0.0001f);
        float maxHScale = horizontalMaxScale.get() <= 0 ? Float.MAX_VALUE : horizontalMaxScale.get();
        float minVScale = Math.max(verticalMinScale.get(), 0.0001f);
        float maxVScale = verticalMaxScale.get() <= 0 ? Float.MAX_VALUE : verticalMaxScale.get();

        float baseHScale = Mth.clamp(1.0f, minHScale, maxHScale);
        float baseVScale = Mth.clamp(1.0f, minVScale, maxVScale);
        float textHeightAtBaseScale = charQuadSize * baseVScale;
        boolean vScrollNeeded = targetH > 0 && textHeightAtBaseScale > targetH;
        
        EScrollMode hMode = horizontalScrollMode.get();
        boolean hScrollActive;

        if (hMode == EScrollMode.ALWAYS) {
            hScrollActive = horizontalScrollingSpeed.get() != 0.0f;
        } else {
            float idealScale = targetW > 0 && cachedUnscaledTextWidth > 0 ? (targetW / cachedUnscaledTextWidth) : 1.0f;
            
            if (hMode == EScrollMode.WHEN_NEEDED && idealScale < minHScale) {
                hScrollActive = horizontalScrollingSpeed.get() != 0.0f;
            } else {
                hScrollActive = false;
            }
        }
        
        if (hScrollActive) {
            cachedFinalHScale = baseHScale;
        } else {
            float idealScale = targetW > 0 && cachedUnscaledTextWidth > 0 ? (targetW / cachedUnscaledTextWidth) : 1.0f;
            
            if (hMode == EScrollMode.FLEX_FIT && idealScale < minHScale) {
                cachedFinalHScale = minHScale;
            } else {
                cachedFinalHScale = Mth.clamp(idealScale, minHScale, maxHScale);
            }
        }

        EScrollMode vMode = verticalScrollMode.get();
        boolean vScrollActive = verticalScrollingSpeed.get() != 0.0f && (vMode == EScrollMode.ALWAYS || (vMode == EScrollMode.WHEN_NEEDED && vScrollNeeded));
        
        if (vScrollActive) {
            cachedFinalVScale = baseVScale;
        } else {
            float idealScale = targetH > 0 && charQuadSize > 0 ? (targetH / charQuadSize) : 1.0f;
            cachedFinalVScale = Mth.clamp(idealScale, minVScale, maxVScale);
        }
    }

    public void render(DLGraphics graphics) {
        render(graphics, glowing.get() ? LightCoordsUtil.FULL_BRIGHT : graphics.packedLight());
    }

    public void render(DLGraphics graphics, int light) {
        Component component = text.get();
        if (component == null || component.getString().isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (lastRenderTime == 0) ? 0 : (currentTime - lastRenderTime) / 1000.0f;
        lastRenderTime = currentTime;

        final float charQuadSize = 8.0f;

        updateGlyphCache(component);
        calculateFinalScales();

        float targetW = preferredWidth.get();
        float targetH = preferredHeight.get();
        float textX = x.get();
        float textY = y.get();

        float minHScale = Math.max(horizontalMinScale.get(), 0.0001f);
        float minVScale = Math.max(verticalMinScale.get(), 0.0001f);
        float baseVScale = Mth.clamp(1.0f, minVScale, verticalMaxScale.get() <= 0 ? Float.MAX_VALUE : verticalMaxScale.get());
        float textHeightAtBaseScale = charQuadSize * baseVScale;
        boolean vScrollNeeded = targetH > 0 && textHeightAtBaseScale > targetH;

        Rectangle initialBounds = clippingArea.get();
        float finalClipLeft = (float)Math.max(initialBounds.left(), textX);
        float finalClipRight = (float)(targetW > 0 ? Math.min(initialBounds.right(), textX + targetW) : initialBounds.right());
        float finalClipTop = (float)Math.max(initialBounds.top(), textY);
        float finalClipBottom = (float)(targetH > 0 ? Math.min(initialBounds.bottom(), textY + targetH) : initialBounds.bottom());

        EScrollMode hMode = horizontalScrollMode.get();
        float finalHScale = cachedFinalHScale;

        boolean hScrollActive;
        if (hMode == EScrollMode.ALWAYS) {
            hScrollActive = horizontalScrollingSpeed.get() != 0.0f;
        } else {
            float idealScale = targetW > 0 && cachedUnscaledTextWidth > 0 ? (targetW / cachedUnscaledTextWidth) : 1.0f;
            hScrollActive = (hMode == EScrollMode.WHEN_NEEDED && idealScale < minHScale) && horizontalScrollingSpeed.get() != 0.0f;
        }
        
        if (hScrollActive) {
            finalClipLeft = (float)Math.max(initialBounds.left(), textX);
            finalClipRight = (float)(targetW > 0 ? Math.min(initialBounds.right(), textX + targetW) : initialBounds.right());
            
            float totalScrollRange = (float)Math.max(0, finalClipRight - finalClipLeft) + (cachedUnscaledTextWidth * finalHScale);
            horizontalScrollOffset += horizontalScrollingSpeed.get() * deltaTime;
            horizontalScrollOffset %= totalScrollRange;
        } else {
            if (hMode == EScrollMode.FLEX_FIT && (targetW > 0 && cachedUnscaledTextWidth > 0) && (targetW / cachedUnscaledTextWidth) < minHScale) {
                finalClipRight = (float)initialBounds.right();
            }
            horizontalScrollOffset = 0;
        }

        EScrollMode vMode = verticalScrollMode.get();
        float finalVScale = cachedFinalVScale;
        boolean vScrollActive = verticalScrollingSpeed.get() != 0.0f && (vMode == EScrollMode.ALWAYS || (vMode == EScrollMode.WHEN_NEEDED && vScrollNeeded));
        
        if (vScrollActive) {
            finalClipTop = (float)Math.max(initialBounds.top(), textY);
            finalClipBottom = (float)(targetH > 0 ? Math.min(initialBounds.bottom(), textY + targetH) : initialBounds.bottom());
            
            float totalScrollRange = (float)Math.max(0, finalClipBottom - finalClipTop) + (charQuadSize * finalVScale);
            verticalScrollOffset += verticalScrollingSpeed.get() * deltaTime;
            verticalScrollOffset %= totalScrollRange;
        } else {
            verticalScrollOffset = 0;
        }
        
        Rectangle finalClipBounds = Rectangle.withSize(finalClipLeft, finalClipTop, Math.max(0, finalClipRight - finalClipLeft), Math.max(0, finalClipBottom - finalClipTop));

        if (targetW <= 0.0f || targetH <= 0.0f || finalClipBounds.width() <= 0 || finalClipBounds.height() <= 0) {
            return;
        }

        float totalScaledWidth = cachedUnscaledTextWidth * finalHScale;
        float totalScaledHeight = charQuadSize * finalVScale;

        float finalX = (float)(hScrollActive ? (float)finalClipBounds.right() - horizontalScrollOffset : textX);
        float finalY = (float)(vScrollActive ? (float)finalClipBounds.bottom() - verticalScrollOffset : textY);

        if (!hScrollActive) {
            ETextAlignment alignment = horizontalAlign.get();
            
            if (targetW > 0) {
                if (alignment == ETextAlignment.CENTER) {
                    finalX = textX + (targetW - totalScaledWidth) / 2.0f;
                } else if (alignment == ETextAlignment.RIGHT) {
                    finalX = textX + targetW - totalScaledWidth;
                }
            } else {
                if (alignment == ETextAlignment.CENTER) finalX -= totalScaledWidth / 2.0f;
                else if (alignment == ETextAlignment.RIGHT) finalX -= totalScaledWidth;
            }
        }

        DLColor bgColor = backgroundColor.get();
        boolean hasBg = bgColor != null && bgColor.getAlphaF() > 0;
        if (hasBg) {
            PaddingF padding = backgroundPadding.get();
            
            float bgX, bgY, bgW, bgH;

            if (fullBackground.get()) {
                bgX = textX;
                bgY = textY;
                
                bgW = targetW;
                if (bgW <= 0 || (hMode == EScrollMode.FLEX_FIT && totalScaledWidth > bgW)) {
                    bgW = totalScaledWidth;
                }

                bgH = targetH;
                if (bgH <= 0 || (vMode == EScrollMode.FLEX_FIT && totalScaledHeight > bgH)) {
                    bgH = totalScaledHeight;
                }
            } else {
                bgX = finalX;
                bgY = finalY;
                bgW = totalScaledWidth;
                bgH = totalScaledHeight;
            }

            float drawX = bgX - padding.left();
            float drawY = bgY - padding.top();
            float drawW = bgW + padding.left() + padding.right();
            float drawH = bgH + padding.top() + padding.bottom();

            float visibleX = Math.max(drawX, (float)Math.max(finalClipBounds.left() - padding.left(), initialBounds.left()));
            float visibleY = Math.max(drawY, (float)Math.max(finalClipBounds.top() - padding.top(), initialBounds.top()));
            float visibleRight = Math.min(drawX + drawW, (float)Math.min(finalClipBounds.right() + padding.right(), initialBounds.right()));
            float visibleBottom = Math.min(drawY + drawH, (float)Math.min(finalClipBounds.bottom() + padding.bottom(), initialBounds.bottom()));
            
            float visibleW = visibleRight - visibleX;
            float visibleH = visibleBottom - visibleY;

            if (visibleW > 0 && visibleH > 0) {
                RenderUtils.fillColor(graphics, new Vector3f(visibleX, visibleY, 0.0f), visibleW, visibleH, bgColor, Direction.NORTH, light, false);
            }
            
            /*
            RenderUtils.drawDebugLine(graphics, new Vector3f((float)visibleX, visibleY, 0), new Vector3f(visibleRight, visibleY, 0), DLColor.GREEN);
            RenderUtils.drawDebugLine(graphics, new Vector3f((float)visibleX, visibleY, 0), new Vector3f((float)visibleX, visibleBottom, 0), DLColor.GREEN);
            RenderUtils.drawDebugLine(graphics, new Vector3f(visibleRight, visibleY, 0), new Vector3f(visibleRight, visibleBottom, 0), DLColor.GREEN);
            RenderUtils.drawDebugLine(graphics, new Vector3f((float)visibleX, visibleBottom, 0), new Vector3f(visibleRight,visibleBottom, 0), DLColor.GREEN);
            */
        }

        graphics.poseStack().pushPose();
        graphics.poseStack().translate(finalX, finalY, 0.002);
        if (hasBg) {
            graphics.poseStack().translate(0, 0, 0.02);
        }

        float currentUnscaledX = 0f;
        for (StyledChar styledChar : cachedGlyphs) {
            CharData charData = styledChar.charData();
            Style style = styledChar.style();
            float glyphAdvance = styledChar.unscaledAdvance();

            float quadWidth = charQuadSize * finalHScale;
            float quadHeight = charQuadSize * finalVScale;
            float quadWorldX = finalX + currentUnscaledX * finalHScale;
            float quadWorldY = finalY;

            if (quadWorldX + quadWidth < finalClipBounds.left() || quadWorldX > finalClipBounds.right() ||
                quadWorldY + quadHeight < finalClipBounds.top() || quadWorldY > finalClipBounds.bottom()) {
                currentUnscaledX += glyphAdvance;
                continue;
            }

            int finalColor = color.get().getAsARGB();
            if (style.getColor() != null) {
                finalColor = style.getColor().getValue() | 0xFF000000;
            }

            graphics.poseStack().pushPose();
            graphics.poseStack().scale(finalHScale, finalVScale, 1.0f);
            graphics.poseStack().translate(currentUnscaledX, 0, 0);

            // The visible part of the label, in this character's own coordinates (1.21 scaled the quad
            // and shifted the glyph's texture coordinates by the same fractions).
            float clipLeft = (float)((finalClipBounds.left() - finalX) / finalHScale - currentUnscaledX);
            float clipRight = (float)((finalClipBounds.right() - finalX) / finalHScale - currentUnscaledX);
            float clipTop = (float)((finalClipBounds.top() - finalY) / finalVScale);
            float clipBottom = (float)((finalClipBounds.bottom() - finalY) / finalVScale);

            GlyphSource glyphs = fontUtils.glyphs(style.getFont());
            BakedGlyph baked = glyphs.getGlyph(charData.charCode());
            if (style.isObfuscated() && charData.charCode() != 32) {
                baked = glyphs.getRandomGlyph(RANDOM, (int) Math.ceil(baked.info().getAdvance(false)));
            }
            // 1.21 created the render output with dropShadow = style.isObfuscated(); kept as it was.
            int shadowColor = style.isObfuscated() ? ARGB.scaleRGB(finalColor, 0.25F) : 0;
            float boldOffset = style.isBold() ? baked.info().getBoldOffset() : 0.0F;
            TextRenderable.Styled glyph = baked.createGlyph(0, 0, finalColor, shadowColor, style, boldOffset, baked.info().getShadowOffset());
            Matrix4f pose = graphics.poseStack().last().pose();
            if (glyph != null) {
                VertexConsumer target = graphics.multiBufferSource().getBuffer(glyph.renderType(Font.DisplayMode.NORMAL));
                glyph.render(IDENTITY, new ClippedGlyphConsumer(target, pose, clipLeft, clipTop, clipRight, clipBottom), light, false);
            }
            if (style.isUnderlined() || style.isStrikethrough()) {
                VertexConsumer target = graphics.vertexConsumer(RenderUtils.BLANK_TEXTURE_LOCATION);
                ClippedGlyphConsumer effect = new ClippedGlyphConsumer(target, pose, clipLeft, clipTop, clipRight, clipBottom);
                if (style.isStrikethrough()) {
                    addEffect(effect, -1.0F, 3.5F, glyphAdvance, 4.5F, finalColor, light);
                }
                if (style.isUnderlined()) {
                    addEffect(effect, -1.0F, 8.0F, glyphAdvance, 9.0F, finalColor, light);
                }
            }

            graphics.poseStack().popPose();

            currentUnscaledX += glyphAdvance;
        }

        graphics.poseStack().popPose();

        /*
        RenderUtils.drawDebugLine(graphics, new Vector3f((float)initialBounds.x(), (float)initialBounds.y(), 0), new Vector3f((float)initialBounds.right(), (float)initialBounds.y(), 0), DLColor.RED);
        RenderUtils.drawDebugLine(graphics, new Vector3f((float)initialBounds.x(), (float)initialBounds.y(), 0), new Vector3f((float)initialBounds.x(), (float)initialBounds.bottom(), 0), DLColor.RED);
        RenderUtils.drawDebugLine(graphics, new Vector3f((float)initialBounds.right(), (float)initialBounds.y(), 0), new Vector3f((float)initialBounds.right(), (float)initialBounds.bottom(), 0), DLColor.RED);
        RenderUtils.drawDebugLine(graphics, new Vector3f((float)initialBounds.x(), (float)initialBounds.bottom(), 0), new Vector3f((float)initialBounds.right(), (float)initialBounds.bottom(), 0), DLColor.RED);
        
        RenderUtils.drawDebugLine(graphics, new Vector3f((float)finalClipBounds.x(), (float)finalClipBounds.y(), 0), new Vector3f((float)finalClipBounds.right(), (float)finalClipBounds.y(), 0), DLColor.YELLOW);
        RenderUtils.drawDebugLine(graphics, new Vector3f((float)finalClipBounds.x(), (float)finalClipBounds.y(), 0), new Vector3f((float)finalClipBounds.x(), (float)finalClipBounds.bottom(), 0), DLColor.YELLOW);
        RenderUtils.drawDebugLine(graphics, new Vector3f((float)finalClipBounds.right(), (float)finalClipBounds.y(), 0), new Vector3f((float)finalClipBounds.right(), (float)finalClipBounds.bottom(), 0), DLColor.YELLOW);
        RenderUtils.drawDebugLine(graphics, new Vector3f((float)finalClipBounds.x(), (float)finalClipBounds.bottom(), 0), new Vector3f((float)finalClipBounds.right(), (float)finalClipBounds.bottom(), 0), DLColor.YELLOW);
        */
    }

    private static void addEffect(VertexConsumer consumer, float x0, float y0, float x1, float y1, int color, int light) {
        consumer.addVertex(x0, y0, 0.01F).setColor(color).setUv(0, 0).setLight(light);
        consumer.addVertex(x0, y1, 0.01F).setColor(color).setUv(0, 1).setLight(light);
        consumer.addVertex(x1, y1, 0.01F).setColor(color).setUv(1, 1).setLight(light);
        consumer.addVertex(x1, y0, 0.01F).setColor(color).setUv(1, 0).setLight(light);
    }

    /**
     * NeoForge 26.2 port: takes a character's quads in its own coordinates, keeps the part inside the
     * clip rectangle (texture coordinates cut in proportion, as 1.21 did by shifting the glyph's), and
     * writes the result transformed by {@code pose}. Quads arrive as top-left, bottom-left,
     * bottom-right, top-right, the order glyphs are drawn in.
     */
    private static final class ClippedGlyphConsumer implements VertexConsumer {
        private final VertexConsumer target;
        private final Matrix4f pose;
        private final float left, top, right, bottom;
        private final float[] x = new float[4], y = new float[4], z = new float[4], u = new float[4], v = new float[4];
        private final int[] color = new int[4], light = new int[4];
        private int n = -1;

        ClippedGlyphConsumer(VertexConsumer target, Matrix4f pose, float left, float top, float right, float bottom) {
            this.target = target;
            this.pose = pose;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        @Override
        public VertexConsumer addVertex(float px, float py, float pz) {
            if (n == 3) {
                flush();
            }
            n++;
            x[n] = px;
            y[n] = py;
            z[n] = pz;
            u[n] = 0;
            v[n] = 0;
            color[n] = 0xFFFFFFFF;
            light[n] = 0;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor(ARGB.color(a, r, g, b));
        }

        @Override
        public VertexConsumer setColor(int c) {
            if (n >= 0) color[n] = c;
            return this;
        }

        @Override
        public VertexConsumer setUv(float pu, float pv) {
            if (n >= 0) {
                u[n] = pu;
                v[n] = pv;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int pu, int pv) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int pu, int pv) {
            if (n >= 0) light[n] = (pu & 0xFFFF) | (pv & 0xFFFF) << 16;
            if (n == 3) flush();
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        private void flush() {
            if (n != 3) {
                n = -1;
                return;
            }
            n = -1;
            float xl = (x[0] + x[1]) / 2, xr = (x[2] + x[3]) / 2;
            float yt = (y[0] + y[3]) / 2, yb = (y[1] + y[2]) / 2;
            if (xr <= left || xl >= right || yb <= top || yt >= bottom) {
                return;
            }
            float s0 = xr > xl ? Math.max(0, (left - xl) / (xr - xl)) : 0;
            float s1 = xr > xl ? Math.min(1, (right - xl) / (xr - xl)) : 1;
            float t0 = yb > yt ? Math.max(0, (top - yt) / (yb - yt)) : 0;
            float t1 = yb > yt ? Math.min(1, (bottom - yt) / (yb - yt)) : 1;
            emit(s0, t0);
            emit(s0, t1);
            emit(s1, t1);
            emit(s1, t0);
        }

        /** Bilinear point (s along the top/bottom edges, t down the left/right edges) of the quad. */
        private void emit(float s, float t) {
            float px = lerp(lerp(x[0], x[3], s), lerp(x[1], x[2], s), t);
            float py = lerp(lerp(y[0], y[3], s), lerp(y[1], y[2], s), t);
            float pz = lerp(lerp(z[0], z[3], s), lerp(z[1], z[2], s), t);
            float pu = lerp(lerp(u[0], u[3], s), lerp(u[1], u[2], s), t);
            float pv = lerp(lerp(v[0], v[3], s), lerp(v[1], v[2], s), t);
            target.addVertex(pose, px, py, pz).setColor(color[0]).setUv(pu, pv).setLight(light[0]);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }

    public float getRenderedWidth() {
        if (this.cachedGlyphs == null) {
            updateGlyphCache(this.text.get());
        }
        calculateFinalScales();
        
        return this.cachedUnscaledTextWidth * this.cachedFinalHScale;
    }

    public float getRenderedHeight() {
        if (this.cachedGlyphs == null) {
            updateGlyphCache(this.text.get());
        }
        calculateFinalScales();
        
        return 8.0f * this.cachedFinalVScale;
    }
}