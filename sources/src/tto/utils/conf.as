package tto.utils 
{
	import flash.filesystem.File;
	import flash.system.Capabilities;
	/**
	 * ...
	 * @author Mao
	 */
	public class conf
	{
		public static const supportedLanguages:Object = {en_US:'English', fr_FR:'Français', de_DE:'Deutsch', ja_JA:'日本語'};
		public static var DATAS:Object = { };
		
		private static const confFile:String = 'My Games/Triple Triad Online/UserSettings.json';
		private static var _loaded:Boolean = false;
		
		public function conf() 
		{
			super();
		}
		
		
		public static function load():void {
			var confFile:File = TTOFiles.getFile(confFile, TTOFiles.DOC_DIR, true);
			var JSON_DATAS:String = TTOFiles.readFile(confFile);
			if (JSON_DATAS == '') {
				// on enregistre un objet vide pour initialiser le json
				DATAS.background_volume = 1;
				DATAS.noise_volume = 1;
				
				var mainLanguage:String = Capabilities.languages[0];
				if (supportedLanguages[mainLanguage] !== null && supportedLanguages[mainLanguage] !== undefined) {
					DATAS.language = Capabilities.languages[0];
				} else {
					DATAS.language = 'en_US';
				}
				
				save();
			} else {
				DATAS = JSON.parse(TTOFiles.readFile(confFile));
			}
			
			SoundManager.BACKGROUND_VOLUME = DATAS.background_volume;
			SoundManager.NOISE_VOLUME = DATAS.noise_volume;
			
			_loaded = true;
		}
		
		public static function save():void {
			var JSON_DATAS:String = JSON.stringify(DATAS);
			if (_loaded) TTOFiles.writeFile(confFile, TTOFiles.DOC_DIR, JSON_DATAS, true); 
		}
	}

}