package com.telemaxx.mapsforgesrv;

import java.util.ArrayList;
import java.util.List;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.labels.MapDataStoreLabelStore;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

/**
 * The label store behind the task option neighbor-labels: DatabaseRenderer asks it for the labels
 * of a tile's 3x3 neighborhood, from the tile above-left to the tile below-right.
 *
 * At the edges of the world those two tiles wrap around (the tile left of x=0 is the last column),
 * so the neighborhood of an edge tile arrives as an inverted range, and MapDataStore reads nothing
 * for an inverted range: the tile would lose all its labels. This store reads such a range as the
 * pieces on either side of the wrap instead. Labels from the far side of the world lie nowhere near
 * the tile and are not drawn on it.
 */
public class NeighborLabelStore extends MapDataStoreLabelStore {

	public NeighborLabelStore(MapDataStore mapDataStore, RenderThemeFuture renderThemeFuture, float textScale,
			DisplayModel displayModel, GraphicFactory graphicFactory) {
		super(mapDataStore, renderThemeFuture, textScale, displayModel, graphicFactory);
	}

	@Override
	public List<MapElementContainer> getVisibleItems(Tile upperLeft, Tile lowerRight) {
		int max = Tile.getMaxTileNumber(upperLeft.zoomLevel);
		if (max < 2) {
			// A world of 1x1 or 2x2 tiles is narrower than a 3x3 neighborhood: at zoom 1 the tiles
			// above-left and below-right of (0,0) are both (1,1). The neighborhood is the whole world.
			return super.getVisibleItems(new Tile(0, 0, upperLeft.zoomLevel, upperLeft.tileSize),
					new Tile(max, max, upperLeft.zoomLevel, upperLeft.tileSize));
		}
		if (upperLeft.tileX <= lowerRight.tileX && upperLeft.tileY <= lowerRight.tileY) {
			return super.getVisibleItems(upperLeft, lowerRight);
		}
		List<MapElementContainer> items = new ArrayList<>();
		for (int[] xs : pieces(upperLeft.tileX, lowerRight.tileX, max)) {
			for (int[] ys : pieces(upperLeft.tileY, lowerRight.tileY, max)) {
				items.addAll(super.getVisibleItems(
						new Tile(xs[0], ys[0], upperLeft.zoomLevel, upperLeft.tileSize),
						new Tile(xs[1], ys[1], upperLeft.zoomLevel, upperLeft.tileSize)));
			}
		}
		return items;
	}

	// The range from first to last as non-inverted pieces, wrapping past max back to 0.
	static List<int[]> pieces(int first, int last, int max) {
		List<int[]> pieces = new ArrayList<>();
		if (first <= last) {
			pieces.add(new int[] { first, last });
		} else {
			pieces.add(new int[] { first, max });
			pieces.add(new int[] { 0, last });
		}
		return pieces;
	}
}
