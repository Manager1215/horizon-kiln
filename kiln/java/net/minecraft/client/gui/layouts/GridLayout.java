package net.minecraft.client.gui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Kiln's stand-in for {@code GridLayout}: children in rows and columns. Each column is as wide as its widest
 * child, each row as tall as its tallest (a child over several takes what it lacks from the last of them), with the
 * spacing between; each child is aligned in its cell by its settings.
 */
public class GridLayout extends AbstractLayout {
    public final List<ChildContainer> children = new ArrayList<>();
    private final LayoutSettings defaultCellSettings = LayoutSettings.defaults();
    private int rowSpacing;
    private int columnSpacing;

    public GridLayout() {
        this(0, 0);
    }

    public GridLayout(int x, int y) {
        super(x, y, 0, 0);
    }

    @Override
    public void arrangeElements() {
        super.arrangeElements();
        int rows = 0;
        int columns = 0;
        for (ChildContainer child : children) {
            rows = Math.max(rows, child.getLastOccupiedRow() + 1);
            columns = Math.max(columns, child.getLastOccupiedColumn() + 1);
        }
        int[] widths = new int[columns];
        int[] heights = new int[rows];
        for (ChildContainer child : children) {
            if (child.occupiedColumns == 1) {
                widths[child.column] = Math.max(widths[child.column], child.getWidth());
            }
            if (child.occupiedRows == 1) {
                heights[child.row] = Math.max(heights[child.row], child.getHeight());
            }
        }
        for (ChildContainer child : children) {
            if (child.occupiedColumns > 1) {
                int lacks = child.getWidth() - span(widths, child.column, child.occupiedColumns, columnSpacing);
                if (lacks > 0) {
                    widths[child.getLastOccupiedColumn()] += lacks;
                }
            }
            if (child.occupiedRows > 1) {
                int lacks = child.getHeight() - span(heights, child.row, child.occupiedRows, rowSpacing);
                if (lacks > 0) {
                    heights[child.getLastOccupiedRow()] += lacks;
                }
            }
        }
        for (ChildContainer child : children) {
            child.setX(getX() + span(widths, 0, child.column, columnSpacing) + (child.column > 0 ? columnSpacing : 0),
                    span(widths, child.column, child.occupiedColumns, columnSpacing));
            child.setY(getY() + span(heights, 0, child.row, rowSpacing) + (child.row > 0 ? rowSpacing : 0),
                    span(heights, child.row, child.occupiedRows, rowSpacing));
        }
        width = span(widths, 0, columns, columnSpacing);
        height = span(heights, 0, rows, rowSpacing);
    }

    /** How long {@code count} cells from {@code first} are, with the spacing between them. */
    private static int span(int[] sizes, int first, int count, int spacing) {
        int total = 0;
        for (int i = first; i < first + count && i < sizes.length; i++) {
            total += sizes[i];
        }
        return total + Math.max(0, count - 1) * spacing;
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column) {
        return addChild(child, row, column, newCellSettings());
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, LayoutSettings settings) {
        return addChild(child, row, column, 1, 1, settings);
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, Consumer<LayoutSettings> settings) {
        return addChild(child, row, column, 1, 1, settings);
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, int rows, int columns) {
        return addChild(child, row, column, rows, columns, newCellSettings());
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, int rows, int columns,
            LayoutSettings settings) {
        if (rows < 1 || columns < 1) {
            throw new IllegalArgumentException("A child takes at least one row and one column");
        }
        children.add(new ChildContainer(child, row, column, rows, columns, settings));
        return child;
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, int rows, int columns,
            Consumer<LayoutSettings> settings) {
        LayoutSettings made = newCellSettings();
        settings.accept(made);
        return addChild(child, row, column, rows, columns, made);
    }

    public GridLayout columnSpacing(int spacing) {
        this.columnSpacing = spacing;
        return this;
    }

    public GridLayout rowSpacing(int spacing) {
        this.rowSpacing = spacing;
        return this;
    }

    public GridLayout spacing(int spacing) {
        return columnSpacing(spacing).rowSpacing(spacing);
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        children.forEach(child -> visitor.accept(child.child));
    }

    @Override
    public void removeChildren() {
        children.clear();
    }

    public LayoutSettings newCellSettings() {
        return defaultCellSettings.copy();
    }

    public LayoutSettings defaultCellSetting() {
        return defaultCellSettings;
    }

    /** Fills the grid in order, {@code columns} to a row. */
    public RowHelper createRowHelper(int columns) {
        return new RowHelper(columns);
    }

    public final class RowHelper {
        private final int columns;
        private int index;

        RowHelper(int columns) {
            this.columns = columns;
        }

        public <T extends LayoutElement> T addChild(T child) {
            return addChild(child, 1);
        }

        public <T extends LayoutElement> T addChild(T child, int occupiedColumns) {
            return addChild(child, occupiedColumns, defaultCellSetting());
        }

        public <T extends LayoutElement> T addChild(T child, LayoutSettings settings) {
            return addChild(child, 1, settings);
        }

        public <T extends LayoutElement> T addChild(T child, int occupiedColumns, LayoutSettings settings) {
            int row = index / columns;
            int column = index % columns;
            if (column + occupiedColumns > columns) {
                row++;
                column = 0;
                index = row * columns;
            }
            index += occupiedColumns;
            return GridLayout.this.addChild(child, row, column, 1, occupiedColumns, settings);
        }

        public GridLayout getGrid() {
            return GridLayout.this;
        }

        public LayoutSettings newCellSettings() {
            return GridLayout.this.newCellSettings();
        }

        public LayoutSettings defaultCellSetting() {
            return GridLayout.this.defaultCellSetting();
        }
    }

    public static class ChildContainer extends AbstractLayout.AbstractChildWrapper {
        final int row;
        final int column;
        final int occupiedRows;
        final int occupiedColumns;

        ChildContainer(LayoutElement child, int row, int column, int occupiedRows, int occupiedColumns,
                LayoutSettings settings) {
            super(child, settings.getExposed());
            this.row = row;
            this.column = column;
            this.occupiedRows = occupiedRows;
            this.occupiedColumns = occupiedColumns;
        }

        protected ChildContainer() {
            super();
            row = column = 0;
            occupiedRows = occupiedColumns = 1;
        }

        public int getLastOccupiedRow() {
            return row + occupiedRows - 1;
        }

        public int getLastOccupiedColumn() {
            return column + occupiedColumns - 1;
        }
    }
}
