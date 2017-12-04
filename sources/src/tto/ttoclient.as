package tto {
	import feathers.system.DeviceCapabilities;
	import flash.display.Sprite;
	import flash.display.Stage;
	import flash.events.Event;
	import flash.geom.Rectangle;
	import starling.core.Starling;
	import tto.utils.conf;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class ttoclient extends Sprite {
		private var _starling:Starling;
		
		public function ttoclient() {
			this.addEventListener(Event.ADDED_TO_STAGE, launch);
			if (DeviceCapabilities.dpi < 100)
				DeviceCapabilities.dpi = 72 * 2.5;
		}
		
		private function launch(e:Event):void {
			this.removeEventListener(Event.ADDED_TO_STAGE, launch);
			conf.load();
			starlingStart();
		}
		
		private function starlingStart():void {
			//var boot:ttoboot = this.parent as ttoboot;
			_starling = new Starling(Game, this.stage as Stage, new Rectangle(0, 0, 1200, 800));
			_starling.showStats = true;
			_starling.start();
		}
	}

}