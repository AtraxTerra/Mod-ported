package de.mrjulsen.mcdragonlib.client.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import de.mrjulsen.mcdragonlib.client.gui.widgets.util.EAlign;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.TiledBlitRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Utility class providing a collection of static helper methods for GUI rendering
 * and common in-GUI operations used by DragonLib's client code.
 *
 * <p>Responsibilities include:
 * - Screen / mouse coordinate helpers
 * - Scissor (clipping) helpers
 * - Texture binding and drawing (including tiled drawing)
 * - Color and tint helpers
 * - Rendering helpers for items, entities, blocks and models into GUI contexts
 * - Basic shape and text drawing helpers
 *
 * <p>All methods in this class are stateless and operate on provided graphics
 * contexts or global Minecraft client state.
 *
 * <p>NeoForge 26.2 port: the 26.2 GUI is retained-mode ({@link GuiGraphicsExtractor}); nothing binds
 * textures, sets shader colors or toggles depth/blend state any more. The texture and tint set with
 * {@link #setTexture} and {@link #setTint} are therefore kept here and applied by the drawing helpers of
 * this class (the 1.21 shader color tinted every draw call, these helpers included). Scissor calls keep
 * their 1.21 meaning (one clip rectangle, replaced by the next call) on top of the 26.2 scissor stack.
 */
public class GuiUtils {

    /**
     * Specifies how a texture should be filled into a rectangular area.
     * STRETCH scales the source region to fit the destination area.
     * TILE repeats the source region to fill the destination area.
     */
    public static enum TextureFillMode {
        STRETCH,
        TILE
    }
    

    /**
     * Returns the current mouse X coordinate in GUI-scaled pixels.
     *
     * @return mouse X position relative to the GUI scale
     */
    public static double mouseXOnScreen() {
        return Minecraft.getInstance().mouseHandler.xpos() * (double)Minecraft.getInstance().getWindow().getGuiScaledWidth() / (double)Minecraft.getInstance().getWindow().getScreenWidth();
    }

    /**
     * Returns the current mouse Y coordinate in GUI-scaled pixels.
     *
     * @return mouse Y position relative to the GUI scale
     */
    public static double mouseYOnScreen() {
        return Minecraft.getInstance().mouseHandler.ypos() * (double)Minecraft.getInstance().getWindow().getGuiScaledHeight() / (double)Minecraft.getInstance().getWindow().getScreenHeight();
    }

    /**
     * Returns the current GUI-scaled screen width.
     *
     * @return width in GUI-scaled pixels
     */
    public static double getScreenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    /**
     * Returns the current GUI-scaled screen height.
     *
     * @return height in GUI-scaled pixels
     */
    public static double getScreenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /**
     * Enables OpenGL scissor test (clipping) for the specified rectangular area.
     *
     * @param graphics the DLGuiGraphics context (not modified)
     * @param area the rectangle in GUI coordinates to enable scissor for
     */
    public static void enableScissor(DLGuiGraphics graphics, Rectangle area) {
        enableScissor(graphics, (int)area.x(), (int)area.y(), (int)area.width(), (int)area.height());
    }

    /**
     * Enables OpenGL scissor test (clipping) for the specified rectangular area.
     *
     * @param graphics the DLGuiGraphics context (not modified)
     * @param x left coordinate in GUI pixels
     * @param y top coordinate in GUI pixels
     * @param w width in GUI pixels
     * @param h height in GUI pixels
     */
    public static void enableScissor(DLGuiGraphics graphics, int x, int y, int w, int h) {
        // 1.21: RenderSystem.enableScissor in window pixels, replacing any active clip. 26.2 keeps a scissor
        // stack in GUI coordinates, transformed by the current pose: push with an identity pose (the
        // rectangle is already in screen space) and pop the previous DragonLib clip first.
        GuiGraphicsExtractor g = graphics.graphics();
        if (scissorOwner != g) {
            scissorOwner = g;
            scissorActive = false;
        }
        if (scissorActive) {
            g.disableScissor();
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.identity();
        g.enableScissor(x, y, x + Math.max(0, w), y + Math.max(0, h));
        pose.popMatrix();
        scissorActive = true;
    }

    /**
     * Disables any previously enabled scissor (clipping) region.
     *
     * @param graphics the DLGuiGraphics context (not modified)
     */
    public static void disableScissor(DLGuiGraphics graphics) {
        GuiGraphicsExtractor g = graphics.graphics();
        if (scissorOwner == g && scissorActive) {
            g.disableScissor();
            scissorActive = false;
        }
    }

    /** The extractor whose scissor stack holds DragonLib's current clip (a new extractor each frame). */
    private static @Nullable GuiGraphicsExtractor scissorOwner;
    private static boolean scissorActive;

    /**
     * Plays the standard UI button click sound at default pitch and volume.
     */
    public static void playButtonSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /**
     * Converts a FormattedText instance to a visual-order FormattedCharSequence.
     *
     * @param text the formatted text to convert
     * @return a FormattedCharSequence suitable for rendering
     */
    public static FormattedCharSequence toFormattedCharSequence(FormattedText text) {
        return text instanceof Component ? ((Component)text).getVisualOrderText() : Language.getInstance().getVisualOrder(text);
    }
    
    
    /**
     * Splits a collection of formatted texts into visual-order char sequences
     * using the provided font and maximum line width.
     *
     * @param font font used for measuring and splitting
     * @param components collection of formatted texts to split
     * @param maxWidth maximum width in pixels for each resulting line
     * @param <T> a type extending FormattedText
     * @return list of resulting FormattedCharSequence lines
     */
    public static <T extends FormattedText> List<FormattedCharSequence> splitToFormattedCharSequences(Font font, Collection<T> components, int maxWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>(components.size());
        for (T component : components) {
            lines.addAll(font.split(component, maxWidth));
        }
        return lines;
    }
    
    /**
     * Splits a collection of formatted texts into FormattedText line objects
     * using the provided font and maximum line width.
     *
     * @param font font used for measuring and splitting
     * @param components collection of formatted texts to split
     * @param maxWidth maximum width in pixels for each resulting line
     * @param <T> a type extending FormattedText
     * @return list of resulting FormattedText lines
     */
    public static <T extends FormattedText> List<FormattedText> splitText(Font font, Collection<T> components, int maxWidth) {
        List<FormattedText> lines = new ArrayList<>(components.size());
        for (T component : components) {
            lines.addAll(font.getSplitter().splitLines(component, maxWidth, Style.EMPTY));
        }
        return lines;
    }

    /**
     * Draws a tooltip comprised of multiple formatted text lines at the specified location.
     *
     * @param graphics DLGuiGraphics wrapper used for rendering
     * @param font font used to render tooltip text
     * @param x x coordinate where tooltip should be shown
     * @param y y coordinate where tooltip should be shown
     * @param lines list of formatted text lines to display
     * @param maxWidth maximum width for tooltip text wrapping
     */
    public static void drawTooltip(DLGuiGraphics graphics, Font font, int x, int y, List<? extends FormattedText> lines, int maxWidth) {
        // 26.2 draws tooltips at the end of the frame, in screen coordinates: map the local position
        // through the current pose (1.21 drew the tooltip immediately, inside that pose).
        Vector2f screen = graphics.graphics().pose().transformPosition(x, y, new Vector2f());
        graphics.graphics().setTooltipForNextFrame(font, splitToFormattedCharSequences(font, lines, maxWidth), (int) screen.x(), (int) screen.y());
    }

    /**
     * Draws a tooltip at a position adjusted directly from the raw input (useful for mouse-based tooltips).
     *
     * @param graphics DLGuiGraphics wrapper used for rendering
     * @param font font used to render the tooltip
     * @param x raw x coordinate (will be offset internally)
     * @param y raw y coordinate (will be offset internally)
     * @param lines tooltip lines
     * @param maxWidth maximum width for tooltip text wrapping
     */
    public static void drawTooltipDirectlyAt(DLGuiGraphics graphics, Font font, int x, int y, List<? extends FormattedText> lines, int maxWidth) {
        drawTooltip(graphics, font, x - 8, y - 16, lines, maxWidth);
    }

    /**
     * Builds a descriptive tooltip for a translatable enum class, including
     * the enum description and each enum constant's name and description.
     *
     * @param enumClass the enum class to describe (must implement ITranslatableEnum)
     * @param maxWidth maximum width to consider when splitting lines (unused by current implementation but kept for API compatibility)
     * @param <T> enum type
     * @return list of Components representing the tooltip lines
     */
    public static <T extends Enum<T> & ITranslatableEnum> List<Component> getEnumTooltipData(Class<T> enumClass, int maxWidth) {
        List<Component> c = new ArrayList<>();
        T enumValue = enumClass.getEnumConstants()[0];
        c.add(enumValue.getEnumDescriptionTranslation());
        c.add(TextUtils.text(" "));
        for (T t : enumClass.getEnumConstants()) {
            c.add(TextUtils.text("> ").withStyle(ChatFormatting.BOLD).append(t.getValueTranslation()).withStyle(ChatFormatting.BOLD));
            c.add(t.getValueDescriptionTranslation().withStyle(ChatFormatting.GRAY));
        }
        return c;
    }



    /**
     * Binds the given Identifier as the active texture for subsequent draw calls.
     *
     * @param texture the texture resource location to bind
     */
    public static void setTexture(Identifier texture) {
        currentTexture = texture;
        currentDynamicTexture = null;
    }

    /**
     * Binds the given OpenGL texture id as the active texture for subsequent draw calls.
     *
     * @param textureId the OpenGL texture id
     */
    public static void setTexture(AbstractTexture texture) {
        currentDynamicTexture = texture;
        currentTexture = null;
    }

    /** Texture set by {@link #setTexture}, used by {@link #renderTiledTexture(DLGuiGraphics, Runnable, int, int, int, int, float, float, float, float, int, int)}. */
    private static @Nullable Identifier currentTexture;
    private static @Nullable AbstractTexture currentDynamicTexture;
    /** Tint set by {@link #setTint} (ARGB, white = none), applied by this class's drawing helpers. */
    private static int tint = 0xFFFFFFFF;

    /** The current tint (ARGB). */
    public static int getTint() {
        return tint;
    }

    /** {@code color} multiplied by the current tint. */
    public static int tinted(int color) {
        return tint == 0xFFFFFFFF ? color : ARGB.multiply(color, tint);
    }

    /**
     * Sets the current shader color/tint from a DLColor instance.
     *
     * @param color color used to set the shader color (including alpha)
     */
    public static void setTint(DLColor color) {
        tint = color.getAsARGB();
    }

    /**
     * Resets the shader color/tint to opaque white (no tint).
     */
    public static void resetTint() {
        tint = 0xFFFFFFFF;
    }

    /**
     * Renders a texture repeatedly (tiled) to fill the specified area.
     *
     * @param graphics DLGuiGraphics context used for matrix access
     * @param bindTexture a Runnable that binds the appropriate texture when executed
     * @param x destination x coordinate
     * @param y destination y coordinate
     * @param w destination width
     * @param h destination height
     * @param u source texture u coordinate
     * @param v source texture v coordinate
     * @param uW source width in texture pixels
     * @param vH source height in texture pixels
     * @param texWidth full texture atlas width (for UV normalization)
     * @param texHeight full texture atlas height (for UV normalization)
     */
    public static void renderTiledTexture(DLGuiGraphics graphics, Runnable bindTexture, int x, int y, int w, int h, float u, float v, float uW, float vH, int texWidth, int texHeight) {
        // 26.2: no texture binding; bindTexture selects the texture through setTexture.
        bindTexture.run();
        AbstractTexture texture = currentDynamicTexture != null ? currentDynamicTexture
            : currentTexture != null ? Minecraft.getInstance().getTextureManager().getTexture(currentTexture) : null;
        if (texture != null) {
            renderTiledTexture(graphics, texture, x, y, w, h, u, v, uW, vH, texWidth, texHeight);
        }
    }

    /** Tiled drawing of {@code texture}: whole tiles of the source region, then the cut remainder. */
    public static void renderTiledTexture(DLGuiGraphics graphics, AbstractTexture texture, int x, int y, int w, int h, float u, float v, float uW, float vH, int texWidth, int texHeight) {
        if (w <= 0 || h <= 0 || uW <= 0 || vH <= 0) {
            return;
        }
        GuiGraphicsExtractor g = graphics.graphics();
        g.submitGuiElementRenderState(new TiledBlitRenderState(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
            new Matrix3x2f(g.pose()),
            Math.max(1, (int) uW), Math.max(1, (int) vH),
            x, y, x + w, y + h,
            u / texWidth, (u + uW) / texWidth, v / texHeight, (v + vH) / texHeight,
            tint,
            g.peekScissorStack()
        ));
    }

    /**
     * Draws a texture resource at specified destination area. Supports STRETCH and TILE fill modes.
     *
     * @param texture texture resource to draw
     * @param graphics DLGuiGraphics wrapper used for rendering
     * @param x destination x
     * @param y destination y
     * @param w destination width
     * @param h destination height
     * @param u source u in texture
     * @param v source v in texture
     * @param uW source width in texture pixels
     * @param vH source height in texture pixels
     * @param mode fill mode (STRETCH or TILE)
     */
    public static void drawTexture(Identifier texture, DLGuiGraphics graphics, int x, int y, int w, int h, int u, int v, int uW, int vH, TextureFillMode mode) {
        drawTexture(texture, graphics, x, y, w, h, u, v, uW, vH, mode, 256, 256);
    }

    /**
     * Draws a texture resource at specified destination area with an explicit texture atlas size.
     *
     * @param texture texture resource to draw
     * @param graphics DLGuiGraphics wrapper used for rendering
     * @param x destination x
     * @param y destination y
     * @param w destination width
     * @param h destination height
     * @param u source u in texture
     * @param v source v in texture
     * @param uW source width in texture pixels
     * @param vH source height in texture pixels
     * @param mode fill mode (STRETCH or TILE)
     * @param textureWidth full texture atlas width for UV calculation
     * @param textureHeight full texture atlas height for UV calculation
     */
    public static void drawTexture(Identifier texture, DLGuiGraphics graphics, int x, int y, int w, int h, int u, int v, int uW, int vH, TextureFillMode mode, int textureWidth, int textureHeight) {
        switch (mode) {
            case TILE -> renderTiledTexture(graphics, Minecraft.getInstance().getTextureManager().getTexture(texture), x, y, w, h, u, v, uW, vH, textureWidth, textureHeight);
            default -> graphics.graphics().blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, uW, vH, textureWidth, textureHeight, tint);
        }
    }

    /**
     * Draws a texture by OpenGL texture id, supporting tiled and stretched modes.
     *
     * @param textureId OpenGL texture id to bind
     * @param graphics DLGuiGraphics wrapper used for rendering
     * @param x destination x
     * @param y destination y
     * @param w destination width
     * @param h destination height
     * @param u source u in texture
     * @param v source v in texture
     * @param uW source width in texture pixels
     * @param vH source height in texture pixels
     * @param mode fill mode (STRETCH or TILE)
     * @param textureWidth full texture atlas width for UV calculation
     * @param textureHeight full texture atlas height for UV calculation
     */
    public static void drawTexture(AbstractTexture texture, DLGuiGraphics graphics, int x, int y, int w, int h, int u, int v, int uW, int vH, TextureFillMode mode, int textureWidth, int textureHeight) {
        // 1.21 took a raw OpenGL texture id here; 26.2 textures are GPU objects, so the texture itself.
        switch (mode) {
            case TILE -> renderTiledTexture(graphics, texture, x, y, w, h, u, v, uW, vH, textureWidth, textureHeight);
            default -> {
                GuiVertexRecorder quad = GuiVertexRecorder.textured(graphics.graphics(), texture);
                Matrix4f matrix = graphics.poseStack().last().pose();
                float u0 = (float) u / textureWidth, u1 = (float) (u + uW) / textureWidth;
                float v0 = (float) v / textureHeight, v1 = (float) (v + vH) / textureHeight;
                quad.addVertex(matrix, x, y, 0).setUv(u0, v0).setColor(tint);
                quad.addVertex(matrix, x, y + h, 0).setUv(u0, v1).setColor(tint);
                quad.addVertex(matrix, x + w, y + h, 0).setUv(u1, v1).setColor(tint);
                quad.addVertex(matrix, x + w, y, 0).setUv(u1, v0).setColor(tint);
                quad.submit();
            }
        }
    }

    /**
     * Draws a DLTexture wrapper which may either use a Identifier or a texture id.
     *
     * @param texture DLTexture wrapper
     * @param graphics DLGuiGraphics wrapper used for rendering
     * @param x destination x
     * @param y destination y
     * @param w destination width
     * @param h destination height
     * @param u source u in texture
     * @param v source v in texture
     * @param uW source width in texture pixels
     * @param vH source height in texture pixels
     * @param mode fill mode (STRETCH or TILE)
     */
    public static void drawTexture(DLTexture texture, DLGuiGraphics graphics, int x, int y, int w, int h, int u, int v, int uW, int vH, TextureFillMode mode) {
        if (texture.getTexture().isPresent()) {
            drawTexture(texture.getTexture().get(), graphics, x, y, w, h, u, v, uW, vH, mode, texture.width(), texture.height());
        } else if (texture.getDynamicTexture().isPresent()) {
            drawTexture(texture.getDynamicTexture().get(), graphics, x, y, w, h, u, v, uW, vH, mode, texture.width(), texture.height());
        }
    }
    
    /**
     * Convenience overload that draws the entire DLTexture to the destination rectangle
     * using STRETCH mode.
     *
     * @param texture DLTexture wrapper
     * @param graphics DLGuiGraphics wrapper used for rendering
     * @param x destination x
     * @param y destination y
     * @param w destination width (also used as source width)
     * @param h destination height (also used as source height)
     * @param u source u in texture
     * @param v source v in texture
     */
    public static void drawTexture(DLTexture texture, DLGuiGraphics graphics, int x, int y, int w, int h, int u, int v) {
        drawTexture(texture, graphics, x, y, w, h, u, v, w, h, TextureFillMode.STRETCH);
    }
    
    /**
     * Convenience overload that draws the entire DLTexture to the destination rectangle
     * using STRETCH mode and source region equal to destination size.
     *
     * @param texture DLTexture wrapper
     * @param graphics DLGuiGraphics wrapper used for rendering
     * @param x destination x
     * @param y destination y
     * @param w destination width
     * @param h destination height
     */
    public static void drawTexture(DLTexture texture, DLGuiGraphics graphics, int x, int y, int w, int h) {
        drawTexture(texture, graphics, x, y, w, h, 0, 0, w, h, TextureFillMode.STRETCH);
    }





    /**
     * Fills a rectangle area with the provided color.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param area rectangle to fill
     * @param color color to fill with (including alpha)
     */
    public static void fill(DLGuiGraphics graphics, Rectangle area, DLColor color) {
        fill(graphics, (int)area.x(), (int)area.y(), (int)area.width(), (int)area.height(), color);
    }

    /**
     * Fills a rectangle area with the provided color.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x left coordinate
     * @param y top coordinate
     * @param w width
     * @param h height
     * @param color color to fill with (including alpha)
     */
    public static void fill(DLGuiGraphics graphics, int x, int y, int w, int h, DLColor color) {
        graphics.graphics().fill(x, y, x + w, y + h, tinted(color.getAsARGB()));
    }

    /**
     * Fills a rectangle with a gradient between two colors aligned according to the given alignment.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param area rectangle to fill
     * @param colorA first color
     * @param colorB second color
     * @param align alignment determining vertex color order
     */
    public static void fillGradient(DLGuiGraphics graphics, Rectangle area, DLColor colorA, DLColor colorB, EAlign align) {
        fillGradient(graphics, (int)area.x(), (int)area.y(), (int)area.width(), (int)area.height(), colorA, colorB, align);
    }

    /**
     * Fills a rectangle with a gradient between two colors aligned according to the given alignment.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x left coordinate
     * @param y top coordinate
     * @param w width
     * @param h height
     * @param colorA first color
     * @param colorB second color
     * @param align alignment determining vertex color order
     */
    public static void fillGradient(DLGuiGraphics graphics, int x, int y, int w, int h, DLColor colorA, DLColor colorB, EAlign align) {
        DLColor[] vertexColors = new DLColor[4];
        for (int i = 0; i < vertexColors.length; i++) {
            vertexColors[(align.getOrder() + i) % vertexColors.length] = (i < 2 ? colorA : colorB);
        }

        GuiVertexRecorder buffer = GuiVertexRecorder.colored(graphics);
        Matrix4f matrix = graphics.poseStack().last().pose();
        buffer.addVertex(matrix, x + w, y, 0).setColor(tinted(vertexColors[0].getAsARGB()));
        buffer.addVertex(matrix, x, y, 0).setColor(tinted(vertexColors[1].getAsARGB()));
        buffer.addVertex(matrix, x, y + h, 0).setColor(tinted(vertexColors[2].getAsARGB()));
        buffer.addVertex(matrix, x + w, y + h, 0).setColor(tinted(vertexColors[3].getAsARGB()));
        buffer.submit();
    }

    /**
     * Draws a filled box with a solid fill color and a border color.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param area rectangle describing the box
     * @param fillColor interior fill color
     * @param borderColor outline color
     */
    public static void drawBox(DLGuiGraphics graphics, Rectangle area, DLColor fillColor, DLColor borderColor) {
        drawBox(graphics, (int)area.x(), (int)area.y(), (int)area.width(), (int)area.height(), fillColor, borderColor);
    }

    /**
     * Draws a filled box with a solid fill color and a border color.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x left coordinate
     * @param y top coordinate
     * @param w width
     * @param h height
     * @param fillColor interior fill color
     * @param borderColor outline color
     */
    public static void drawBox(DLGuiGraphics graphics, int x, int y, int w, int h, DLColor fillColor, DLColor borderColor) {
        fill(graphics, x, y, w, h, fillColor);
        fill(graphics, x, y, w, 1, borderColor);
        fill(graphics, x, y + h - 1, w, 1, borderColor);
        fill(graphics, x, y + 1, 1, h - 2, borderColor);
        fill(graphics, x + w - 1, y + 1, 1, h - 2, borderColor);
    }

    /**
     * Draws a string with the specified alignment and optional drop shadow.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param font font used to render text
     * @param x x coordinate for the text anchor
     * @param y y coordinate for the text baseline
     * @param text plain string to render
     * @param color color to use for text
     * @param alignment left / center / right alignment mode
     * @param dropShadow whether to render a drop shadow
     */
    public static void drawString(DLGuiGraphics graphics, Font font, int x, int y, String text, DLColor color, ETextAlignment alignment, boolean dropShadow) {
        drawString(graphics, font, x, y, TextUtils.text(text), color, alignment, dropShadow);
    }

    /**
     * Draws a formatted text with the specified alignment and optional drop shadow.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param font font used to render text
     * @param x x coordinate for the text anchor
     * @param y y coordinate for the text baseline
     * @param text formatted text to render
     * @param color color to use for text
     * @param alignment left / center / right alignment mode
     * @param dropShadow whether to render a drop shadow
     */
    public static void drawString(DLGuiGraphics graphics, Font font, int x, int y, FormattedText text, DLColor color, ETextAlignment alignment, boolean dropShadow) {
        int width = font.width(text);
        int offset = 0;
        switch (alignment) {
            default:
            case LEFT:
                break;
            case CENTER:
                offset = -width / 2;
                break;
            case RIGHT:
                offset = -width;
                break;
        }

        graphics.graphics().text(font, toFormattedCharSequence(text), x + offset, y, tinted(color.getAsARGB()), dropShadow);
    }

    /**
     * Renders an item stack into the GUI at default scale and with decorations.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param stack item stack to render
     * @param x x coordinate
     * @param y y coordinate
     */
    public static void renderItem(DLGuiGraphics graphics, ItemStack stack, int x, int y) {
        renderItem(graphics, stack, x, y, 1, true);
    }

    /**
     * Renders an item stack into the GUI with custom scale and optional decorations.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param stack item stack to render
     * @param x x coordinate
     * @param y y coordinate
     * @param scale uniform scale factor to apply
     * @param drawDecorations whether to draw stack count, durability, etc.
     */
    public static void renderItem(DLGuiGraphics graphics, ItemStack stack, int x, int y, float scale, boolean drawDecorations) {
        graphics.poseStack().pushPose();
        graphics.poseStack().translate(x, y, 0);
        graphics.poseStack().scale(scale, scale, 1);
        graphics.graphics().item(stack, 0, 0);
        if (drawDecorations) {
            graphics.graphics().itemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        }
        graphics.poseStack().popPose();
    }
    

    /**
     * Renders only the item decorations (count, text overlays) for the specified item stack.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param stack the item stack whose decorations to render
     * @param x x coordinate
     * @param y y coordinate
     * @param scale uniform scale factor to apply to decoration rendering
     * @param text optional overlay text to render (may be null or empty)
     */
    public static void renderItemDecoration(DLGuiGraphics graphics, ItemStack stack, int x, int y, float scale, String text) {
        graphics.poseStack().pushPose();
        graphics.poseStack().translate(x, y, 0);
        graphics.poseStack().scale(scale, scale, 1);
        graphics.graphics().itemDecorations(Minecraft.getInstance().font, stack, 0, 0, text);
        graphics.poseStack().popPose();
    }

    /**
     * Renders a living entity into GUI space at default brightness.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x x coordinate
     * @param y y coordinate
     * @param entity entity instance to render
     */
    public static void renderEntity(DLGuiGraphics graphics, int x, int y, LivingEntity entity) {
        renderEntity(graphics, x, y, 1, entity, FULL_BRIGHT);
    }

    /** Packed light for full brightness (LightTexture.FULL_BRIGHT in 1.21). */
    public static final int FULL_BRIGHT = 0xF000F0;

    /**
     * Renders a living entity into GUI space with a custom scale and brightness.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x x coordinate
     * @param y y coordinate
     * @param scale uniform scale factor to apply to the entity rendering
     * @param entity entity instance to render
     * @param light packed light coordinate controlling brightness
     */
    public static void renderEntity(DLGuiGraphics graphics, int x, int y, float scale, LivingEntity entity, int light) {
        renderEntity(graphics, x, y, scale, entity, new Matrix4f(), new Quaternionf(), light);
    }

    /**
     * Renders a living entity into GUI space applying a transformation matrix and optional camera orientation override.
     *
     * <p>This overload allows fine-grained control of the transform and camera orientation used
     * when rendering the entity for UI previews.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x x coordinate
     * @param y y coordinate
     * @param scale uniform scale factor
     * @param entity entity instance to render
     * @param transformation additional model transformation matrix to apply
     * @param cameraOrientation optional quaternion representing camera orientation (may be null)
     * @param light packed light to use for rendering
     */
    public static void renderEntity(DLGuiGraphics graphics, int x, int y, float scale, LivingEntity entity, Matrix4f transformation, @Nullable Quaternionf cameraOrientation, int light) {
        // 26.2 renders GUI entities as pictures in picture: the entity's render state goes into a box,
        // drawn later into its own texture. The box stands on (x, y), feet at its bottom edge; the box
        // is placed with the current pose (1.21 drew straight into the GUI buffers inside that pose).
        float size = 16 * scale;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
        EntityRenderState renderState = renderer.createRenderState(entity, 1.0F);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        int halfWidth = (int) Math.ceil(Math.max(renderState.boundingBoxWidth, 1) * size) + 2;
        int height = (int) Math.ceil(Math.max(renderState.boundingBoxHeight, 1) * size) + 2;
        Vector2f origin = graphics.graphics().pose().transformPosition(x, y, new Vector2f());
        Quaternionf rotation = transformation.getNormalizedRotation(new Quaternionf());
        Vector3f translation = new Vector3f(0, renderState.boundingBoxHeight / 2.0F, 0);
        // The 26.2 entity picture renderer conjugates the camera orientation itself (1.21 callers did).
        graphics.graphics().entity(renderState, size, translation, rotation, cameraOrientation,
            (int) origin.x() - halfWidth, (int) origin.y() - height, (int) origin.x() + halfWidth, (int) origin.y());
    }
    

    /**
     * Renders an entity that rotates following the mouse position (default scale).
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x x coordinate
     * @param y y coordinate
     * @param entity entity to render
     */
    public static void renderEntityFollowingMouse(DLGuiGraphics graphics, int x, int y, LivingEntity entity) {
        renderEntityFollowingMouse(graphics, x, y, 1, entity);
    }

    /**
     * Renders an entity that rotates following the mouse position.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x x coordinate
     * @param y y coordinate
     * @param scale uniform scale factor
     * @param entity entity to render
     */
    public static void renderEntityFollowingMouse(DLGuiGraphics graphics, int x, int y, float scale, LivingEntity entity) {
        renderEntityFollowingMouse(graphics, x, y, scale, (float)Minecraft.getInstance().mouseHandler.xpos(), (float)Minecraft.getInstance().mouseHandler.ypos(), entity, FULL_BRIGHT);
    }

    /**
     * Renders an entity that rotates following a given screen-space mouse coordinate.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x x coordinate
     * @param y y coordinate
     * @param scale uniform scale factor
     * @param screenMouseX mouse X in screen coordinates used to compute rotation
     * @param screenMouseY mouse Y in screen coordinates used to compute rotation
     * @param entity entity to render
     * @param light packed light for rendering
     */
    public static void renderEntityFollowingMouse(DLGuiGraphics graphics, int x, int y, float scale, float screenMouseX, float screenMouseY, LivingEntity entity, int light) {
        Matrix4f transformation = graphics.poseStack().last().pose();
        float aX = (float)Math.atan((double)((transformation.m30() + x - screenMouseX) / 40.0F));
        float aY = (float)Math.atan((double)((transformation.m31() + y - (screenMouseY + entity.getEyeHeight() * (16 * scale))) / 40.0F));
        renderEntityFollowingAngle(graphics, x, y, scale, aX, aY, entity, light);
    }

    /**
     * Renders an entity rotated according to precomputed angle components.
     *
     * @param graphics DLGuiGraphics used for rendering
     * @param x x coordinate
     * @param y y coordinate
     * @param scale uniform scale factor
     * @param angleXComponent X-angle influence component
     * @param angleYComponent Y-angle influence component
     * @param entity entity to render
     * @param light packed light for rendering
     */
    public static void renderEntityFollowingAngle(DLGuiGraphics graphics, int x, int y, float scale, float angleXComponent, float angleYComponent, LivingEntity entity, int light) {
        Quaternionf quaternionf = (new Quaternionf()).rotateZ((float)Math.PI);
        Quaternionf quaternionf1 = (new Quaternionf()).rotateX(angleYComponent * 20.0F * 0.017453292F);
        quaternionf.mul(quaternionf1);
        float f2 = entity.yBodyRot;
        float f3 = entity.getYRot();
        float f4 = entity.getXRot();
        float f5 = entity.yHeadRotO;
        float f6 = entity.yHeadRot;
        entity.yBodyRot = 180.0F + angleXComponent * 20.0F;
        entity.setYRot(180.0F + angleXComponent * 40.0F);
        entity.setXRot(-angleYComponent * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();        
        Matrix4f matrix = new Matrix4f().rotate(quaternionf);
        renderEntity(graphics, x, y, scale, entity, matrix, quaternionf1, light);
        entity.yBodyRot = f2;
        entity.setYRot(f3);
        entity.setXRot(f4);
        entity.yHeadRotO = f5;
        entity.yHeadRot = f6;
    }

    // NeoForge 26.2 port: renderBlockState and renderModel draw through DragonLib's model framework
    // (DLModel, BasicMesh), which is not ported to the 26.2 model pipeline yet. They come back with it.
}
