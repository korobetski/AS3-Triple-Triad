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
	public class CardListThumb extends Sprite {
		private var _id:uint;
		private var _selected:Boolean = false;
		private var _enabled:Boolean;
		private var _cardTex:Image;
		private var _cardBorder:Image;
		
		public function CardListThumb(cardId:uint, cardTex:Texture, border:Texture) {
			_id = cardId
			_selected = false;
			_enabled = true;
			_cardTex = new Image(cardTex);
			addChild(_cardTex);
			_cardBorder = new Image(border);
			_cardBorder.x = _cardBorder.y = -2
			addChild(_cardBorder);
			
			addEventListener(TouchEvent.TOUCH, onTouch);
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
				_cardTex.filter = colorMatrixFilter;
				useHandCursor = false;
			} else {
				_cardTex.filter = null;
				useHandCursor = true;
			}
		}
		
		public function set isSelected(value:Boolean):void {
			_selected = value;
		}
		
		public function get isSelected():Boolean {
			return _selected;
		}
		
		override public function dispose():void 
		{
			removeEventListener(TouchEvent.TOUCH, onTouch);
			super.dispose();
		}
	}

}