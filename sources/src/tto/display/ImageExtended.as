package tto.display {
	import starling.display.Image;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import tto.utils.Assets;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class ImageExtended extends Image {
		private var _textureName:String;
		
		public function ImageExtended(textureName:String) {
			_textureName = textureName;
			super(Assets.manager.getTexture(_textureName));
			
			addEventListener(TouchEvent.TOUCH, onTouch);
		}
		
		public function get textureName():String {
			return _textureName;
		}
		
		public function set textureName(value:String):void {
			_textureName = value;
			this.texture = Assets.manager.getTexture(_textureName);
		}
		
		private function onTouch(event:TouchEvent):void {
			var touch:Touch = event.getTouch(this, TouchPhase.ENDED);
			if (touch) {
				dispatchEventWith(Event.TRIGGERED, true);
			}
		}
		
		override public function dispose():void 
		{
			removeEventListener(TouchEvent.TOUCH, onTouch);
			super.dispose();
		}
	
	}

}