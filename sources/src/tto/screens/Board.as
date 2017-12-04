package tto.screens {
	import feathers.controls.LayoutGroup;
	import feathers.layout.TiledRowsLayout;
	import tto.display.Tile;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Board extends LayoutGroup {
		public var tiles:Vector.<Tile>;
		
		public function Board() {
			super();
			
			var boardLayout:TiledRowsLayout = new TiledRowsLayout();
			boardLayout.useSquareTiles = true;
			this.width = 136 * 3;
			this.height = 136 * 3;
			this.layout = boardLayout;
			
			tiles = new <Tile>[];
			var newTile:Tile
			for (var i:uint = 0; i < 9; i++) {
				newTile = new Tile(Boolean(i % 2));
				newTile.id = i;
				newTile.legacyName = String('p' + (i + 1));
				this.addChild(newTile);
				tiles.push(newTile);
			}
			var tile:Tile
			for (i = 0; i < 9; i++) {
				tile = tiles[i];
				if (i % 3 > 0)
					tile.leftTile = tiles[i - 1];
				if (i > 2)
					tile.topTile = tiles[i - 3];
				if (i < 6)
					tile.bottomTile = tiles[i + 3];
				if (i % 3 < 2)
					tile.rightTile = tiles[i + 1];
			}
		}
		
		public function razBoard():void {
			for each (var tile:Tile in tiles) {
				tile.card = null;
			}
		}
		
		public function elements(predefined:Array = null):void {
			var elem:Array = ["earth", "fire", "holy", "ice", "lightning", "poison", "water", "wind"];
			var tile:Tile
			for (var i:uint = 0; i < 9; i++) {
				tile = this.tiles[i];
				if (predefined) {
					tile.element = predefined[i];
				} else {
					if (tools.rand(1)) {
						tile.element = elem[tools.rand(elem.length - 1)];
					}
				}
			}
		}
		
		public function getRemainingTiles():Array {
			var remaining:Array = [];
			var tile:Tile
			for (var i:uint = 0; i < 9; i++) {
				tile = tiles[i];
				if (!tile.card)
					remaining.push(tile);
			}
			return remaining;
		}
		
		override public function dispose():void {
			super.dispose();
		}
	}

}