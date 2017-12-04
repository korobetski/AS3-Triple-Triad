package tto.display {
	import starling.display.Image;
	import starling.display.Sprite;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import starling.filters.ColorMatrixFilter;
	import starling.textures.Texture;
	import tto.utils.Assets;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class CardThumb extends Sprite {
		private var _id:uint;
		private var _collection:String = 'ff14_';
		private var _TexId:String;
		private var _selected:Boolean = false;
		private var _enabled:Boolean;
		
		public function CardThumb(cardId:int = 0, collection:String = 'ff14_') {
			_id = cardId;
			_collection = collection;
			var _tex:Texture;
			
			if (_id > 0) {
				_TexId = _collection + 'thumb_' + String(_id);
				_tex = Assets.manager.getTexture(_TexId);
			} else {
				_tex = Assets.manager.getTexture('voidCardThumb');
			}
			var pic:Image = new Image(_tex);
			this.addChild(pic);
			
			if (_id > 0) {
				var voidClip:Image = new Image(Assets.manager.getTexture('voidCardThumb'));
				voidClip.x = -2;
				voidClip.y = -2;
				this.addChild(voidClip);
				useHandCursor = true;
				addEventListener(TouchEvent.TOUCH, onTouch);
			}
			
			this.flatten();
		}
		
		private function onTouch(e:TouchEvent):void {
			var touch:Touch = e.getTouch(this, TouchPhase.ENDED);
			if (touch) {
				dispatchEventWith(Event.TRIGGERED, true);
			}
		}
		
		public function get id():uint {
			return _id;
		}
		
		public function get enabled():Boolean {
			return _enabled;
		}
		
		public function set enabled(value:Boolean):void {
			_enabled = value;
			if (!_enabled) {
				// TODO : make a grey card thumbs atlas
				var colorMatrixFilter:ColorMatrixFilter = new ColorMatrixFilter();
				colorMatrixFilter.adjustSaturation(-1); // make image Grayscale
				this.filter = colorMatrixFilter;
			} else {
				this.filter = null;
			}
		}
		
		public function set isSelected(value:Boolean):void {
			_selected = value;
		}
		
		public function get isSelected():Boolean {
			return _selected;
		}
		
		override public function dispose():void {
			removeEventListener(TouchEvent.TOUCH, onTouch);
			super.dispose();
		}
	}

}