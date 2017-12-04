package tto.utils {
	
	/**
	 * ...
	 * @author Mao
	 */
	public class i18n {
		private static var _STR_CODES:Object = {};
		private static var _loaded:Boolean = false;
		static private var _language:String;
		static private var _onComplete:Function;
		
		public function i18n() {
		
		}
		
		public static function loadLanguage(language:String, overrideLanguage:Boolean = false, onComplete:Function = null):void {
			//trace('loadLanguage : '+ arguments)
			if (!_loaded || overrideLanguage) {
				_language = language;
				_onComplete = onComplete;
				_STR_CODES = Assets.manager.getObject(language);
				_loaded = true;
				if (_onComplete !== null)
					_onComplete();
			}
		}
		
		public static function gettext(STR_code:String):String {
			return (_loaded && _STR_CODES[STR_code]) ? _STR_CODES[STR_code] : STR_code;
		}
		
		public static function get loaded():Boolean {
			return _loaded;
		}
		
		static public function get language():String {
			return _language;
		}
	}

}