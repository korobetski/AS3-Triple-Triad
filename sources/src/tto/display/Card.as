package tto.display {
	import feathers.dragDrop.DragData;
	import feathers.dragDrop.DragDropManager;
	import feathers.dragDrop.IDragSource;
	import starling.animation.Transitions;
	import starling.core.Starling;
	import starling.display.ButtonState;
	import starling.display.Image;
	import starling.display.Quad;
	import starling.display.Sprite;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import starling.text.TextField;
	import tto.datas.cards;
	import tto.display.Tile;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.FilterProvider;
	import tto.utils.SoundManager;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Card extends Sprite implements IDragSource {
		public static const GREY_COLOR:uint = 0x5a595a;
		public static const BLUE_COLOR:uint = 0x2d4660;
		public static const RED_COLOR:uint = 0x602d2d;
		
		private var _id:uint = 0;
		private var _collection:String = 'ff14_';
		private var _texId:String = '';
		private var back_bg:Image;
		private var colorBackground:Quad;
		private var selected_bg:Image;
		private var power:Array;
		private var container:Sprite;
		private var _data:Object;
		private var _position:Array;
		private var _tile:Tile;
		private var _modifier:TextField;
		private var _color:String;
		private var mState:String;
		private var _draggable:Boolean;
		private var _flipping:Boolean = false;
		private var _selected:Boolean = false;
		private var _digits:CardDigits;
		private var _legacyName:String;
		private var _index:uint;
		
		[Embed(source="../../../assets/fonts/eurostile.TTF",fontFamily="Eurostile",mimeType="application/x-font",embedAsCFF="true")]
		public static const EUROSTILE:Class;
		
		public function Card() {
			super();
			
			this.width = 104;
			this.height = 128;
			this.pivotX = 52;
			this.pivotY = 64;
			this.touchGroup = true;
			
			selected_bg = new Image(Assets.manager.getTexture('cardSelected'));
			selected_bg.x = -16;
			selected_bg.y = -4;
			selected_bg.visible = false;
			addChild(selected_bg);
			
			_color = 'GREY'
			colorBackground = new Quad(88, 118, 0x5a595a);
			colorBackground.x = 8;
			colorBackground.y = 5;
			addChild(colorBackground);
			
			container = new Sprite();
			addChild(container);
			
			_modifier = new TextField(32, 32, '', 'Eurostile', 18, 0xFFFFFF);
			_modifier.x = 36;
			_modifier.y = 48;
			_modifier.text = "0";
			_modifier.visible = false;
			addChild(_modifier);
			
			_digits = new CardDigits();
			_digits.x = 28;
			_digits.y = 88;
			addChild(_digits);
			
			back_bg = new Image(Assets.manager.getTexture('back'));
			addChild(back_bg);
			
			this.useHandCursor = false;
			this.touchable = false;
			
			addEventListener(TouchEvent.TOUCH, onTouch);
		}
		
		public function set modifier(value:int):void {
			if (value !== 0) {
				_modifier.text = (value > 0) ? String('+' + value) : String(value);
				_modifier.nativeFilters = [FilterProvider.whiteBorder, FilterProvider.blackDropShadow];
				_modifier.visible = true;
			} else {
				_modifier.text = "0";
				_modifier.nativeFilters = [];
				_modifier.visible = false;
			}
		}
		
		public function get modifier():int {
			return int(_modifier.text);
		}
		
		public function set draggable(value:Boolean):void {
			this._draggable = value;
		}
		
		public function get draggable():Boolean {
			return _draggable;
		}
		
		private function onTouch(event:TouchEvent):void {
			if (this.touchable) {
				var touch:Touch = event.getTouch(this);
				if (touch == null) {
					this.visible = true;
					mState = ButtonState.UP;
				} else if (touch.phase == TouchPhase.HOVER) {
					mState = ButtonState.OVER;
				} else if (touch.phase == TouchPhase.BEGAN && mState != ButtonState.DOWN) {
					mState = ButtonState.DOWN;
				} else if (touch.phase == TouchPhase.MOVED && mState == ButtonState.DOWN) {
					if (this._draggable) {
						var dragData:DragData = new DragData();
						dragData.setDataForFormat("card-drag-format", {data: this});
						var ghostCard:Card = new Card();
						ghostCard.draw(String(this._id), Game.PROFILE_DATAS.MODE);
						ghostCard.color = _color;
						DragDropManager.startDrag(this, touch, dragData, ghostCard);
						mState = ButtonState.UP;
					}
				} else if (touch.phase == TouchPhase.ENDED && mState == ButtonState.DOWN) {
					this.visible = true;
					mState = ButtonState.UP;
					dispatchEventWith(Event.TRIGGERED, true);
				}
			}
		}
		
		public function hide():void {
			back_bg.visible = true;
			container.visible = false;
		}
		
		public function show():void {
			back_bg.visible = false;
			container.visible = true;
		}
		
		public function draw(newID:String = 'back', collection:String = 'ff14_'):void {
			_collection = collection;
			_texId = (newID == 'back' || newID == 'blue' || newID == 'red') ? newID : _collection + newID;
			if (container.numChildren > 0)
				tools.purge(container);
			var pic:Image = new Image(Assets.manager.getTexture(_texId));
			container.addChild(pic);
			
			if (uint(newID) > 0) {
				_id = uint(newID);
				var datas:Object = cards.DATAS[_id];
				_data = datas;
				var rc:Image = new Image(Assets.manager.getTexture(String(datas.rarity + 'stars')));
				rc.x = 9;
				rc.y = 6;
				container.addChild(rc);
				if (datas.type) {
					var type:Image = new Image(Assets.manager.getTexture(String('type-' + datas.type)));
					type.x = 80;
					type.y = 3;
					container.addChild(type);
				}
				
				_digits.display(datas.power);
				show();
			} else {
				_id = 0;
				hide();
			}
		}
		
		public function fly(_x:int, _y:int):void {
			Starling.juggler.tween(this, 0.4, {transition: Transitions.EASE_IN, y: this.y - 100, alpha: 0, onComplete: afterFly, onCompleteArgs: [_x, _y]});
		}
		
		private function afterFly(_x:int, _y:int):void {
			this.x = _x + 50;
			this.y = _y - 100;
			this.rotation = -90 * Math.PI / 180;
			this.scaleX = 1.2;
			this.scaleY = 1.2;
			this.show();
			Starling.juggler.tween(this, 0.4, {transition: Transitions.EASE_OUT, x: _x, y: _y, rotation: -360 * Math.PI / 180, scaleX: 1, scaleY: 1, alpha: 1});
		}
		
		public function flyAndSwap(_x:int, _y:int, newId:uint):void {
			Starling.juggler.tween(this, 0.4, {transition: Transitions.EASE_IN, y: this.y - 100, alpha: 0, onComplete: afterFlyAndSwap, onCompleteArgs: [_x, _y, newId]});
		}
		
		private function afterFlyAndSwap(_x:int, _y:int, newId:uint):void {
			this.draw(String(newId), this._collection);
			this.x = _x + 50;
			this.y = _y - 100;
			this.rotation = -90 * Math.PI / 180;
			this.scaleX = 1.2;
			this.scaleY = 1.2;
			Starling.juggler.tween(this, 0.4, {transition: Transitions.EASE_OUT, x: _x, y: _y, rotation: -360 * Math.PI / 180, scaleX: 1, scaleY: 1, alpha: 1});
		}
		
		public function get isFlipping():Boolean {
			return _flipping;
		}
		
		public function flipTo(horizon:Boolean, color:String):void {
			//SoundManager.playSound('flip', true);
			SoundManager.playSound('se_ttriad.scd_157', true);
			_flipping = true;
			if (horizon) {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_IN, scaleX: 0, scaleY: 1.2, onComplete: yoyoTo, onCompleteArgs: [horizon, color]});
			} else {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_IN, scaleX: 1.2, scaleY: 0, onComplete: yoyoTo, onCompleteArgs: [horizon, color]});
			}
		}
		
		private function yoyoTo(horizon:Boolean, color:String):void {
			hide();
			this.color = color;
			
			if (horizon) {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_OUT, scaleX: 1.2, onComplete: unflip, onCompleteArgs: [horizon]});
			} else {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_OUT, scaleY: 1.2, onComplete: unflip, onCompleteArgs: [horizon]});
			}
		}
		
		public function flip(horizon:Boolean = false):void {
			//SoundManager.playSound('flip', true);
			SoundManager.playSound('se_ttriad.scd_157', true);
			_flipping = true;
			if (horizon) {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_IN, scaleX: 0, scaleY: 1.2, onComplete: yoyo, onCompleteArgs: [horizon]});
			} else {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_IN, scaleX: 1.2, scaleY: 0, onComplete: yoyo, onCompleteArgs: [horizon]});
			}
		}
		
		private function yoyo(horizon:Boolean = false):void {
			hide();
			switchColor();
			
			if (horizon) {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_OUT, scaleX: 1.2, onComplete: unflip, onCompleteArgs: [horizon]});
			} else {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_OUT, scaleY: 1.2, onComplete: unflip, onCompleteArgs: [horizon]});
			}
		}
		
		private function unflip(horizon:Boolean = false):void {
			if (horizon) {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_IN, scaleX: 0, scaleY: 1.2, onComplete: yoyo2, onCompleteArgs: [horizon]});
			} else {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_IN, scaleX: 1.2, scaleY: 0, onComplete: yoyo2, onCompleteArgs: [horizon]});
			}
		}
		
		private function yoyo2(horizon:Boolean = false):void {
			show();
			
			if (horizon) {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_OUT, scaleX: 1, scaleY: 1, onComplete: function():void {
						_flipping = false;
					}});
			} else {
				Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_OUT, scaleX: 1, scaleY: 1, onComplete: function():void {
						_flipping = false;
					}});
			}
		
		}
		
		public function backToFront():void {
			hide();
			_flipping = true;
			Starling.juggler.tween(this, 0.1, {transition: Transitions.EASE_OUT, scaleX: 1.2, scaleY: 1.2, onComplete: unflip, onCompleteArgs: [true]});
		}
		
		private function switchColor():void {
			this.color = (_color == 'RED') ? 'BLUE' : 'RED';
		}
		
		public function get id():uint {
			return _id;
		}
		
		public function get data():Object {
			return _data;
		}
		
		public function get type():String {
			return _data.type;
		}
		
		public function get topPow():uint {
			return uint("0x" + _data.power[0]);
		}
		
		public function get rightPow():uint {
			return uint("0x" + _data.power[1]);
		}
		
		public function get bottomPow():uint {
			return uint("0x" + _data.power[2]);
		}
		
		public function get leftPow():uint {
			return uint("0x" + _data.power[3]);
		}
		
		public function get color():String {
			return _color;
		}
		
		public function set color(_c:String):void {
			_color = _c;
			colorBackground.color = Card[_color + '_COLOR'];
			if (this.tile && _c !== "GREY")
				tile.color = _color;
		}
		
		public function get position():Array {
			return _position;
		}
		
		public function set position(arr:Array):void {
			_position = arr;
		}
		
		public function removeListener():void {
		
		}
		
		public function get tile():Tile {
			return _tile;
		}
		
		public function set tile(value:Tile):void {
			_tile = value;
			var mod:int = this.modifier;
			if (_tile && mod !== 0) {
				_tile.topPow = tools.madmax(int(_tile.topPow) + mod);
				_tile.rightPow = tools.madmax(int(_tile.rightPow) + mod);
				_tile.bottomPow = tools.madmax(int(_tile.bottomPow) + mod);
				_tile.leftPow = tools.madmax(int(_tile.leftPow) + mod);
			}
		}
		
		public function get texId():String {
			return _texId;
		}
		
		public function set texId(value:String):void {
			_texId = value;
		}
		
		public function get selected():Boolean {
			return _selected;
		}
		
		public function set selected(value:Boolean):void {
			_selected = value;
			selected_bg.visible = value;
		}
		
		public function get collection():String {
			return _collection;
		}
		
		public function set collection(value:String):void {
			_collection = value;
		}
		
		public function get legacyName():String 
		{
			return _legacyName;
		}
		
		public function set legacyName(value:String):void 
		{
			_legacyName = value;
		}
		
		public function get index():uint 
		{
			return _index;
		}
		
		public function set index(value:uint):void 
		{
			_index = value;
		}
		
		override public function dispose():void {
			tile = null;
			removeEventListener(TouchEvent.TOUCH, onTouch);
			tools.purge(this)
			super.dispose();
		}
	
	}

}