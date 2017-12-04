package tto.controls {
	import feathers.controls.Label;
	import feathers.core.IFeathersEventDispatcher;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import starling.filters.BlurFilter;
	import tto.utils.SoundManager;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class TouchLabel extends Label implements IFeathersEventDispatcher {
		
		public function TouchLabel() {
			super();
			
			this.styleName = "feathers-menu-label";
			this.filter = BlurFilter.createDropShadow(0, 0, 0x000000, 2, 0.5, 0.5);
			
			this.useHandCursor = true;
			this.addEventListener(TouchEvent.TOUCH, onTouch);
		}
		
		private function onTouch(event:TouchEvent):void {
			var touch:Touch = event.getTouch(this);
			if (touch) {
				if (touch.phase == TouchPhase.ENDED) {
					SoundManager.playSound('se_ui.scd_72', true);
					this.filter = BlurFilter.createDropShadow(0, 0, 0x000000, 2, 0.5, 0.5);
					dispatchEventWith(Event.TRIGGERED, true);
				} else if (touch.phase == TouchPhase.BEGAN || touch.phase == TouchPhase.HOVER) {
					this.filter = BlurFilter.createDropShadow(0, 0, 0x33B5E5, 2, 0.5, 0.5);
				}
			} else {
				this.filter = BlurFilter.createDropShadow(0, 0, 0x000000, 2, 0.5, 0.5);
			}
		}
		
		override public function dispose():void {
			this.removeEventListener(TouchEvent.TOUCH, onTouch);
			super.dispose();
		}
	}

}