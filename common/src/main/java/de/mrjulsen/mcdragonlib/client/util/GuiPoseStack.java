package de.mrjulsen.mcdragonlib.client.util;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * NeoForge 26.2 port: the GUI is drawn in two dimensions now, and {@code GuiGraphicsExtractor#pose()}
 * is a {@link Matrix3x2fStack} instead of a {@code PoseStack}. DragonLib's widgets (and the mods built
 * on them) were written against the {@code PoseStack} API, so this class keeps that API on top of the
 * 2D stack: every call goes straight to the extractor's own matrix stack, so vanilla widgets drawn in
 * between see the same transform, exactly as when DragonLib handed them {@code guiGraphics.pose()}.
 *
 * <p>Z components are dropped: the 26.2 GUI orders elements by submission (and strata), not by depth.
 * Rotations keep their effect in the screen plane.
 */
public final class GuiPoseStack {

    private final Matrix3x2fStack stack;

    public GuiPoseStack(Matrix3x2fStack stack) {
        this.stack = stack;
    }

    /** The extractor's own 2D matrix stack. */
    public Matrix3x2fStack matrix() {
        return stack;
    }

    public void pushPose() {
        stack.pushMatrix();
    }

    public void popPose() {
        stack.popMatrix();
    }

    public void translate(double x, double y, double z) {
        stack.translate((float) x, (float) y);
    }

    public void translate(float x, float y, float z) {
        stack.translate(x, y);
    }

    public void scale(float x, float y, float z) {
        stack.scale(x, y);
    }

    /** Applies the part of {@code rotation} that acts in the screen plane (a rotation about Z). */
    public void mulPose(Quaternionfc rotation) {
        stack.rotate(screenAngle(rotation));
    }

    /** Applies a matrix given in PoseStack form; only its screen-plane (x, y) part is kept. */
    public void mulPose(Matrix4f matrix) {
        stack.mul(new Matrix3x2f(matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(), matrix.m30(), matrix.m31()));
    }

    public void rotateAround(Quaternionfc rotation, float x, float y, float z) {
        stack.rotateAbout(screenAngle(rotation), x, y);
    }

    public void setIdentity() {
        stack.identity();
    }

    /** A snapshot of the current transform. */
    public Pose last() {
        return new Pose(new Matrix3x2f(stack));
    }

    private static float screenAngle(Quaternionfc rotation) {
        Vector3f x = rotation.transformPositiveX(new Vector3f());
        return (float) Math.atan2(x.y(), x.x());
    }

    /** The current transform, as the 2D matrix the GUI uses and as the equivalent 4x4 matrix. */
    public record Pose(Matrix3x2f matrix) {
        public Matrix4f pose() {
            return new Matrix4f(
                matrix.m00(), matrix.m01(), 0, 0,
                matrix.m10(), matrix.m11(), 0, 0,
                0, 0, 1, 0,
                matrix.m20(), matrix.m21(), 0, 1
            );
        }
    }
}
