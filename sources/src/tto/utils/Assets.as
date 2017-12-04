package tto.utils {
	import starling.events.Event;
	import starling.events.EventDispatcher;
	import starling.utils.AssetManager;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Assets extends EventDispatcher {
		public static var manager:AssetManager;
		
		public function Assets() {
			super();
			var lang:String = conf.DATAS.language;
			
			manager = new AssetManager();
			manager.verbose = false;
			manager.enqueue(TTOFiles.getFile('datas/locales/' + lang + '.json', TTOFiles.APP_DIR));
			manager.enqueue(TTOFiles.getDirectory('assets/atlas/', TTOFiles.APP_DIR));
			manager.enqueue(TTOFiles.getDirectory('assets/fonts/', TTOFiles.APP_DIR));
			manager.enqueue(TTOFiles.getDirectory('sounds/', TTOFiles.APP_DIR));
			manager.enqueue(TTOFiles.getDirectory('assets/' + lang + '/', TTOFiles.APP_DIR));
		}
		
		public function load():void {
			manager.loadQueue(function(ratio:Number):void {
				if (ratio == 1) {
					dispatchEvent(new Event(Event.COMPLETE));
				}
			});
		}
	
	}

}