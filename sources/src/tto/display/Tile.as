package tto.display {
	import com.adobe.utils.ArrayUtil;
	import feathers.dragDrop.DragDropManager;
	import feathers.dragDrop.IDropTarget;
	import feathers.events.DragDropEvent;
	import starling.display.BlendMode;
	import starling.display.Image;
	import starling.display.Quad;
	import starling.display.Sprite;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import starling.textures.Texture;
	import tto.display.Card;
	import tto.utils.Assets;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Tile extends Sprite implements IDropTarget {
		public static const CARD_DROPED_ON_TILE_EVENT:String = 'CARD_DROPED_ON_TILE_EVENT';
		public static const BLUE_COLOR:uint = 0x43a7c8;
		public static const RED_COLOR:uint = 0xbb594f;
		
		private var gfx:Sprite;
		private var _card:Card;
		private var _taken:Boolean;
		private var _element:String = "none";
		private var _id:uint;
		private var _color:String;
		private var _shape:Quad;
		private var _leftTile:Tile;
		private var _rightTile:Tile;
		private var _bottomTile:Tile;
		private var _topTile:Tile;
		private var _leftPow:uint;
		private var _rightPow:uint;
		private var _bottomPow:uint;
		private var _topPow:uint;
		private var _elementClip:Sprite;
		private var _legacyName:String;
		
		public function Tile(odd:Boolean) {
			super();
			this.touchGroup = true;
			_taken = false;
			
			width = height = 136;
			
			gfx = new Sprite()
			gfx.width = gfx.height = 136;
			addChild(gfx);
			
			var quartTex:Texture = Assets.manager.getTexture('tripletriadpanel.tex')
			var quartTL:Image = new Image(quartTex), quartTR:Image = new Image(quartTex), quartBL:Image = new Image(quartTex), quartBR:Image = new Image(quartTex);
			quartTL.blendMode = BlendMode.NONE;
			gfx.addChild(quartTL);
			quartTR.blendMode = BlendMode.NONE;
			quartTR.scaleX = -1;
			quartTR.x = 136;
			gfx.addChild(quartTR);
			quartBL.blendMode = BlendMode.NONE;
			quartBL.scaleY = -1;
			quartBL.y = 136;
			gfx.addChild(quartBL);
			quartBR.blendMode = BlendMode.NONE;
			quartBR.scaleX = quartBR.scaleY = -1;
			quartBR.x = quartBR.y = 136;
			gfx.addChild(quartBR);
			
			if (!odd) {
				var veil:Quad = new Quad(136, 136, 0x000000, true);
				veil.alpha = 0.15;
				gfx.addChild(veil);
			}
			gfx.flatten();
			
			_shape = new Quad(136, 136, 0xffffff, true);
			_shape.alpha = 0.2;
			_shape.visible = false;
			addChild(_shape);
			
			_elementClip = new Sprite();
			_elementClip.x = _elementClip.y = 136 / 2 - 10;
			addChild(_elementClip);
			
			this.pivotX = this.width / 2;
			this.pivotY = this.height / 2;
			
			addEventListener(DragDropEvent.DRAG_ENTER, onDragEnter);
			addEventListener(DragDropEvent.DRAG_DROP, onDragDrop);
			addEventListener(DragDropEvent.DRAG_EXIT, onDragExit);
			addEventListener(TouchEvent.TOUCH, onTouch);
			useHandCursor = true;
		}
		
		private function onTouch(e:TouchEvent):void {
			var touch:Touch = e.getTouch(this, TouchPhase.ENDED);
			if (touch && !_taken) {
				dispatchEventWith(Event.TRIGGERED, true);
			}
		}
		
		private function onDragEnter(event:DragDropEvent):void {
			if (event.dragData.hasDataForFormat("card-drag-format")) {
				DragDropManager.acceptDrag(this);
			}
		}
		
		private function onDragDrop(event:DragDropEvent):void {
			if (event.dragData.hasDataForFormat("card-drag-format")) {
				if (this.card == null) {
					var eventDatas:Object = event.dragData.getDataForFormat("card-drag-format");
					var cardToDrop:Card = eventDatas.data;
					// OVERKILL POSITIONNING
					cardToDrop.x = -cardToDrop.parent.x - cardToDrop.parent.parent.x + this.x + this.parent.x;
					cardToDrop.y = -cardToDrop.parent.y - cardToDrop.parent.parent.y + this.y + this.parent.y;
					this.card = cardToDrop;
					this.parent.dispatchEventWith(Tile.CARD_DROPED_ON_TILE_EVENT, false, this);
				} else {
					
				}
			}
		}
		
		private function onDragExit(event:DragDropEvent):void {
			if (!event.isDropped) {
			}
		}
		
		public function get card():Card {
			return _card;
		}
		
		public function set card(value:Card):void {
			if (value !== null) {
				_card = value;
				_color = _card.color;
				_taken = true;
				this.useHandCursor = false;
				_topPow = _card.topPow;
				_rightPow = _card.rightPow;
				_bottomPow = _card.bottomPow;
				_leftPow = _card.leftPow;
				_card.tile = this;
			} else {
				_color = null;
				if (_card)
					_card.tile = null;
				_card = null;
				_taken = false;
				this.useHandCursor = true;
				_topPow = _rightPow = _bottomPow = _leftPow = undefined;
				_shape.visible = false;
			}
		}
		
		public function get isTaken():Boolean {
			return _taken;
		}
		
		public function get leftTile():Tile {
			return _leftTile;
		}
		
		public function set leftTile(value:Tile):void {
			_leftTile = value;
		}
		
		public function get rightTile():Tile {
			return _rightTile;
		}
		
		public function set rightTile(value:Tile):void {
			_rightTile = value;
		}
		
		public function get bottomTile():Tile {
			return _bottomTile;
		}
		
		public function set bottomTile(value:Tile):void {
			_bottomTile = value;
		}
		
		public function get topTile():Tile {
			return _topTile;
		}
		
		public function set topTile(value:Tile):void {
			_topTile = value;
		}
		
		public function get leftPow():uint {
			return _leftPow;
		}
		
		public function set leftPow(value:uint):void {
			_leftPow = value;
		}
		
		public function get rightPow():uint {
			return _rightPow;
		}
		
		public function set rightPow(value:uint):void {
			_rightPow = value;
		}
		
		public function get bottomPow():uint {
			return _bottomPow;
		}
		
		public function set bottomPow(value:uint):void {
			_bottomPow = value;
		}
		
		public function get topPow():uint {
			return _topPow;
		}
		
		public function set topPow(value:uint):void {
			_topPow = value;
		}
		
		public function get leftSameWall():Boolean {
			return (!_leftTile && _leftPow == 10) ? true : false;
		}
		
		public function get rightSameWall():Boolean {
			return (!_rightTile && _rightPow == 10) ? true : false;
		}
		
		public function get bottomSameWall():Boolean {
			return (!_bottomTile && _bottomPow == 10) ? true : false;
		}
		
		public function get topSameWall():Boolean {
			return (!_topTile && _topPow == 10) ? true : false;
		}
		
		public function get onSameWall():Boolean {
			return (leftSameWall || rightSameWall || bottomSameWall || topSameWall) ? true : false;
		}
		
		public function get id():uint {
			return _id;
		}
		
		public function set id(value:uint):void {
			_id = value;
		}
		
		public function get color():String {
			return _color;
		}
		
		public function set color(value:String):void {
			_color = value;
			_shape.color = Tile[value + '_COLOR']
			_shape.visible = true;
		}
		
		public function get element():String {
			return _element;
		}
		
		public function set element(value:String):void {
			tools.purge(_elementClip);
			var elem:Array = ["earth", "fire", "holy", "ice", "lightning", "poison", "water", "wind"]
			if (ArrayUtil.arrayContainsValue(elem, value)) {
				_element = value;
				var elementImg:Image = new Image(Assets.manager.getTexture("type-" + _element));
				_elementClip.addChild(elementImg);
			} else {
				_element = null;
			}
		}
		
		public function get legacyName():String {
			return _legacyName;
		}
		
		public function set legacyName(value:String):void {
			_legacyName = value;
		}
		
		override public function dispose():void {
			card = null;
			removeEventListener(DragDropEvent.DRAG_ENTER, onDragEnter);
			removeEventListener(DragDropEvent.DRAG_DROP, onDragDrop);
			removeEventListener(DragDropEvent.DRAG_EXIT, onDragExit);
			removeEventListener(TouchEvent.TOUCH, onTouch);
			tools.purge(this);
			super.dispose();
		}
	}

}