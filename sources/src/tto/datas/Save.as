package tto.datas 
{
	import flash.filesystem.File;
	import tto.utils.CryptoHelper;
	import tto.utils.TTOFiles;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Save 
	{
		public static const SAVE_DIR:String = 'saves/';
		public var DATAS:Object = {};
		
		public function Save() 
		{
			super();
		}
		
		public function setToDefaultValues():void {
			// default values when creating a new game
			DATAS = {};
			DATAS.CREATION_DATE = new Date().getTime();
			DATAS.LAST_SAVE = new Date().getTime();
			DATAS.SAVE_NUMBER = 0;
			DATAS.USERNAME = 'Kuplu Kopo';
			DATAS.MODE = "ff14_";
			DATAS.ADMIN = 0;
			DATAS.CARDS = [1, 3, 6, 7, 10];
			DATAS.DECKS = [{name:'Starter deck', cards:[1, 3, 6, 7, 10]}]; // 5 decks max
			DATAS.STATS = { WINS:0, DEFEATS:0, DRAWS:0, FORFEITS:0 }; // FORFEITS = STARTED_MATCHES - ENDED_MATCHES
			DATAS.BAG = []; // {item:Item(), stack:0}
			DATAS.BOONS = {MGP:0, XP:0, LUCK:0}; // 
			DATAS.MGP = 100;
			DATAS.XP = 0;
			DATAS.LEVEL = 1;
			DATAS.PVP_XP = 0;
			DATAS.RANK = 1;
			DATAS.AVATAR_ID = 'ffxiv_twi03005';
			DATAS.STARTED_MATCHES = 0;
			DATAS.ENDED_MATCHES = 0;
			DATAS.PVE_MATCHES = 0;
			DATAS.PVP_MATCHES = 0;
			DATAS.ACHIEVEMENTS = {};
			DATAS.NPC_W = {};
			DATAS.RULES_W = {};
		}
		
		public function load(profile_name:String):Object {
			var loadedFile:File = TTOFiles.getFile('saves/' + String(profile_name).toLowerCase() + '.sav', TTOFiles.STORAGE_DIR);
			if (loadedFile.exists) {
				//trace(CryptoHelper.decrypt(TTOFiles.readFile(loadedFile)))
				DATAS = JSON.parse(CryptoHelper.decrypt(TTOFiles.readFile(loadedFile)));
				if (!DATAS.MODE) DATAS.MODE = "ff14_";
				if (!DATAS.ACHIEVEMENTS) DATAS.ACHIEVEMENTS = {};
				if (!DATAS.NPC_W) DATAS.NPC_W = {};
				if (!DATAS.RULES_W) DATAS.RULES_W = {};
				DATAS.STATS.FORFEITS = DATAS.STARTED_MATCHES - DATAS.ENDED_MATCHES;
				return DATAS;
			} else {
				return null;
			}
		}
		
		public function loadFile(save:File):Object {
			if (save.exists) {
				//trace(CryptoHelper.decrypt(TTOFiles.readFile(save)))
				DATAS = JSON.parse(CryptoHelper.decrypt(TTOFiles.readFile(save)));
				if (!DATAS.MODE) DATAS.MODE = "ff14_";
				if (!DATAS.ACHIEVEMENTS) DATAS.ACHIEVEMENTS = {};
				if (!DATAS.NPC_W) DATAS.NPC_W = {};
				if (!DATAS.RULES_W) DATAS.RULES_W = {};
				DATAS.STATS.FORFEITS = DATAS.STARTED_MATCHES - DATAS.ENDED_MATCHES;
				return DATAS;
			} else {
				return null;
			}
		}
		
		public function save():void {
			DATAS.LAST_SAVE = new Date().getTime();
			DATAS.SAVE_NUMBER++;
			var jsonSave:String = CryptoHelper.encrypt(JSON.stringify( DATAS ));
			TTOFiles.writeFile('saves/'+String(DATAS.USERNAME).toLowerCase() +' - '+DATAS.CREATION_DATE+'.sav', TTOFiles.STORAGE_DIR, jsonSave, true);
		}
		
		
		
		public static function save(DATAS:Object):void {
			DATAS.LAST_SAVE = new Date().getTime();
			DATAS.SAVE_NUMBER++;
			var jsonSave:String = CryptoHelper.encrypt(JSON.stringify( DATAS ));
			TTOFiles.writeFile('saves/'+String(DATAS.USERNAME).toLowerCase() +' - '+DATAS.CREATION_DATE+'.sav', TTOFiles.STORAGE_DIR, jsonSave, true);
		}
	}

}