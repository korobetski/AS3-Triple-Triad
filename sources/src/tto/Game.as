package tto {
	import feathers.controls.ScreenNavigator;
	import feathers.controls.ScreenNavigatorItem;
	import starling.display.BlendMode;
	import starling.display.Image;
	import starling.display.Sprite;
	import starling.events.Event;
	import starling.textures.Texture;
	import tto.screens.*;
	import tto.theme.TTOTheme;
	import tto.utils.Assets;
	import tto.utils.conf;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Game extends Sprite {
		[Embed(source = "../../assets/bg.jpg")] private const mainBackground:Class;
		private var nav:ScreenNavigator;
		
		private static var nav:ScreenNavigator;
		private var asset:Assets;
		public static var LOGGED_IN:Boolean = false;
		public static var PROFILE_DATAS:Object = {};
		public static var MATCHES:Object = {};
		public static var USERS:Array = [];
		
		public function Game() {
			super();
			
			this.addEventListener(Event.ADDED_TO_STAGE, init);
		}
		
		private function init(e:Event = null):void {
			this.removeEventListener(Event.ADDED_TO_STAGE, init);
			
			asset = new Assets();
			asset.load();
			asset.addEventListener(Event.COMPLETE, assetsLoaded);
			
			var bgTex:Texture = Texture.fromEmbeddedAsset(mainBackground);
			var background:Image = new Image(bgTex);
			background.width = 1200;
			background.height = 800;
			background.blendMode = BlendMode.NONE;
			background.touchable = false
			addChild(background);
			
			nav = new ScreenNavigator();
			Game.nav = nav;
			addChild(nav)
			
			nav.addScreen('MENU_SCREEN', new ScreenNavigatorItem(MenuScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('NEW_GAME_SCREEN', new ScreenNavigatorItem(NewGameScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('LOAD_GAME_SCREEN', new ScreenNavigatorItem(LoadScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('SETTINGS_SCREEN', new ScreenNavigatorItem(SettingsScreen, {gotoScreen: gotoScreenHandler}));
			
			nav.addScreen('DASHBOARD', new ScreenNavigatorItem(dashboardScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('PVE_SCREEN', new ScreenNavigatorItem(PVEScreen, {gotoScreen: gotoScreenHandler, pve_free_mode: prepareMatch}));
			nav.addScreen('PVP_SCREEN', new ScreenNavigatorItem(PVPScreen, {gotoScreen: gotoScreenHandler, pvp_free_mode: prepareMatch}));
			nav.addScreen('PROFILE', new ScreenNavigatorItem(profileScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('CARD_LIST', new ScreenNavigatorItem(cardListScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('CARD_DECKS', new ScreenNavigatorItem(DecksScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('INVENTORY_SCREEN', new ScreenNavigatorItem(InventoryScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('SHOP', new ScreenNavigatorItem(shopScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('HELP_SCREEN', new ScreenNavigatorItem(HelpScreen, {gotoScreen: gotoScreenHandler}));
			
			nav.addScreen('PVE_MATCH_SCREEN', new ScreenNavigatorItem(PVEMatchScreen, {gotoScreen: gotoScreenHandler, pve_free_mode: prepareMatch, sudden_death: prepareMatch}));
			nav.addScreen('PVP_MATCH_SCREEN', new ScreenNavigatorItem(PVPMatchScreen, {gotoScreen: gotoScreenHandler, pvp_free_mode: prepareMatch, sudden_death: prepareMatch}));
			
			nav.addScreen('TUTORIAL_SCREEN', new ScreenNavigatorItem(TutorialScreen, {gotoScreen: gotoScreenHandler}));
			
			nav.addScreen('CCGROUP_SCREEN', new ScreenNavigatorItem(CCGroupScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('CCGROUP_MATCH_SCREEN', new ScreenNavigatorItem(CCGroupMatchScreen, {gotoScreen: gotoScreenHandler, cc_next_match:ccMatch, cc_sudden_death: gsMatch}));
			nav.addScreen('GSGROUP_SCREEN', new ScreenNavigatorItem(GSGroupScreen, {gotoScreen: gotoScreenHandler}));
			nav.addScreen('GSGROUP_MATCH_SCREEN', new ScreenNavigatorItem(GSGroupMatchScreen, {gotoScreen: gotoScreenHandler, gs_next_match:gsMatch, gs_sudden_death: gsMatch}));
			
			nav.addScreen('BACKSTAGE', new ScreenNavigatorItem(BackstageScreen, {gotoScreen: gotoScreenHandler}));
		}
		
		private function assetsLoaded(e:Event):void {
			asset.removeEventListener(Event.COMPLETE, assetsLoaded);
			asset = null;
			i18n.loadLanguage(conf.DATAS.language, false, showMenu);
		}
		
		private function showMenu():void {
			var theme:TTOTheme = new TTOTheme(true);
			nav.showScreen('MENU_SCREEN');
		}
		
		private function connection(e:Event):void {
			gotoScreen('DASHBOARD');
		}
		
		private function gotoScreenHandler(e:Event, data:String):void {
			nav.showScreen(data);
		}
		
		private function gotoScreen(ScreenName:String):void {
			nav.showScreen(ScreenName);
		}
		
		private function prepareMatch(e:Event, data:Object):void {
			nav.clearScreen();
			var item:ScreenNavigatorItem
			if (data._NPC) {
				item = nav.getScreen('PVE_MATCH_SCREEN');
				item.properties = data;
				nav.showScreen('PVE_MATCH_SCREEN');
			} else {
				item = nav.getScreen('PVP_MATCH_SCREEN');
				item.properties = data;
				nav.showScreen('PVP_MATCH_SCREEN');
			}
		}
		
		public static function prepareMatch(data:Object):void {
			nav.clearScreen();
			var item:ScreenNavigatorItem = nav.getScreen('PVP_MATCH_SCREEN');
			item.properties = data;
			nav.showScreen('PVP_MATCH_SCREEN');
		}
		
		public static function ccMatch(e:Event, data:Object):void {
			nav.clearScreen();
			var item:ScreenNavigatorItem = nav.getScreen('CCGROUP_MATCH_SCREEN');
			item.properties = data;
			nav.showScreen('CCGROUP_MATCH_SCREEN');
		}
		
		public static function gsMatch(e:Event, data:Object):void {
			nav.clearScreen();
			var item:ScreenNavigatorItem = nav.getScreen('GSGROUP_MATCH_SCREEN');
			item.properties = data;
			nav.showScreen('GSGROUP_MATCH_SCREEN');
		}
	}

}