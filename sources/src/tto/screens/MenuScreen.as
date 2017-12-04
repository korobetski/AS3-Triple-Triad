package tto.screens {
	import feathers.controls.LayoutGroup;
	import feathers.controls.Screen;
	import feathers.layout.HorizontalLayout;
	import feathers.layout.VerticalLayout;
	import flash.desktop.NativeApplication;
	import flash.events.Event;
	import flash.filesystem.File;
	import starling.display.BlendMode;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import tto.controls.MainButton;
	import tto.datas.Save;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	import tto.utils.TTOFiles;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class MenuScreen extends Screen {
		private var menuItems:Array;
		private var menuContainer:LayoutGroup;
		private var saveFiles:Array;
		
		public function MenuScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			
			menuContainer.width = 1200;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			Game.LOGGED_IN = false;
			
			var logo:Image = new Image(Assets.manager.getTexture('logo_white_512'));
			logo.touchable = false
			logo.x = (1200 - 512) / 2;
			logo.y = 128
			addChild(logo);
			
			saveFiles = TTOFiles.getDirectory(Save.SAVE_DIR, TTOFiles.STORAGE_DIR);
			
			menuItems = [];
			if (saveFiles.length > 0) {
				menuItems.push({label: 'STR_CONTINUE', onTriggred: loadLastSave});
			}
			menuItems.push({label: 'STR_NEW_GAME', screenId: 'NEW_GAME_SCREEN'});
			menuItems.push({label: 'STR_LOAD_GAME', screenId: 'LOAD_GAME_SCREEN'});
			//menuItems.push({label:'STR_HELP', screenId:'RULES_SCREEN'});
			menuItems.push({label: 'STR_SETTINGS', screenId: 'SETTINGS_SCREEN'});
			//menuItems.push( { label:'STR_CREDITS', screenId:'SETTINGS_SCREEN' } );
			menuItems.push({label: 'STR_QUIT', onTriggred: quit});
			
			menuContainer = new LayoutGroup();
			menuContainer.width = stage.width;
			menuContainer.y = 256 + 128;
			
			addChild(menuContainer);
			
			var itemCount:int = menuItems.length;
			var menuItemDatas:Object
			var newMenuItem:MainButton
			
			for (var i:int = 0; i < itemCount; i++) {
				menuItemDatas = menuItems[i];
				newMenuItem = new MainButton(i18n.gettext(menuItemDatas.label));
				menuContainer.addChild(newMenuItem);
			}
			
			menuContainer.addEventListener("triggered", menuController);
			
			var VL:VerticalLayout = new VerticalLayout();
			VL.gap = 8;
			VL.horizontalAlign = VerticalLayout.HORIZONTAL_ALIGN_CENTER;
			menuContainer.layout = VL;
		}
		
		private function loadLastSave():void {
			var lastSave:Save;
			var save:Save
			for each (var saveFile:File in saveFiles) {
				save = new Save();
				save.loadFile(saveFile);
				
				if (!lastSave || lastSave.DATAS.LAST_SAVE < save.DATAS.LAST_SAVE) {
					lastSave = save;
				}
			}
			
			Game.LOGGED_IN = true;
			Game.PROFILE_DATAS = lastSave.DATAS;
			Game.PROFILE_DATAS.STATS.FORFEITS = Game.PROFILE_DATAS.STARTED_MATCHES - Game.PROFILE_DATAS.ENDED_MATCHES;
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		private function menuController(e:*):void {
			var index:int = menuContainer.getChildIndex(e.target as DisplayObject);
			var data:Object = menuItems[index];
			
			if (data.screenId) {
				dispatchEventWith('gotoScreen', false, data.screenId);
			}
			if (data.onTriggred) {
				data.onTriggred();
			}
		}
		
		private function quit():void {
			var exitingEvent:Event = new Event(Event.EXITING, false, true);
			NativeApplication.nativeApplication.dispatchEvent(exitingEvent);
			if (!exitingEvent.isDefaultPrevented()) {
				NativeApplication.nativeApplication.exit();
			} else {
				NativeApplication.nativeApplication.exit();
			}
		}
		
		override public function dispose():void {
			menuContainer.removeEventListener("triggered", menuController);
			super.dispose();
		}
	}
}