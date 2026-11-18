package kiln;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A shape of boxes (see {@link VoxelShape}): what {@code Shapes.empty}, {@code box} and {@code or} give. */
public final class BoxShape extends VoxelShape {
    public static final BoxShape EMPTY = new BoxShape(List.of());

    private BoxShape(List<AABB> boxes) {
        super((net.minecraft.world.phys.shapes.DiscreteVoxelShape) null);
        this.boxes = boxes;
    }

    public static VoxelShape of(List<AABB> boxes) {
        return boxes.isEmpty() ? EMPTY : new BoxShape(List.copyOf(boxes));
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        DoubleArrayList coords = new DoubleArrayList();
        for (AABB box : boxes) {
            double a = axis == Direction.Axis.X ? box.minX : axis == Direction.Axis.Y ? box.minY : box.minZ;
            double b = axis == Direction.Axis.X ? box.maxX : axis == Direction.Axis.Y ? box.maxY : box.maxZ;
            if (!coords.contains(a)) {
                coords.add(a);
            }
            if (!coords.contains(b)) {
                coords.add(b);
            }
        }
        coords.sort(null);
        return coords;
    }
}
