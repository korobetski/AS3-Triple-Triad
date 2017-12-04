package tto.screens {
	import feathers.controls.Alert;
	import feathers.controls.Button;
	import feathers.controls.Label;
	import feathers.controls.List;
	import feathers.controls.Panel;
	import feathers.controls.Screen;
	import feathers.data.ListCollection;
	import flash.filesystem.File;
	import starling.display.Image;
	import starling.events.Event;
	import tto.datas.Save;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	import tto.utils.TTOFiles;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class LoadScreen extends Screen {
		private var saveList:List;
		private var panel:Panel;
		private var backButton:Button;
		private var deleteButton:Button;
		private var loadButton:Button;
		private var alert:Alert;
		
		public function LoadScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			panel.width = stage.width / 2 - 16;
			panel.height = stage.height - 206;
			saveList.width = panel.width - 16;
			
			backButton.y = panel.height - 60 - (deleteButton.height + 8);
			deleteButton.x = backButton.x + backButton.width + 16;
			deleteButton.y = panel.height - 60 - (deleteButton.height + 8);
			loadButton.x = deleteButton.x + deleteButton.width + 16;
			loadButton.y = panel.height - 60 - (deleteButton.height + 8);
		}
		
		override protected function initialize():void {
			super.initialize();
			
			panel = new Panel();
			panel.title = i18n.gettext('STR_SAVES_LIST');
			panel.horizontalScrollPolicy = Panel.SCROLL_POLICY_OFF;
			panel.x = 8;
			panel.y = 150;
			addChild(panel);
			
			saveList = new List();
			saveList.dataProvider = loadSavesFiles();
			saveList.addEventListener(Event.CHANGE, saveList_changeHandler);
			panel.addChild(saveList);
			
			deleteButton = new Button();
			deleteButton.isEnabled = deleteButton.useHandCursor = false;
			deleteButton.label = i18n.gettext("STR_DELETE");
			deleteButton.styleName = Button.ALTERNATE_STYLE_NAME_DANGER_BUTTON;
			deleteButton.addEventListener(Event.TRIGGERED, deleteButton_triggeredHandler);
			panel.addChild(deleteButton)
			
			loadButton = new Button();
			loadButton.isEnabled = loadButton.useHandCursor = false;
			loadButton.label = i18n.gettext("STR_LOAD");
			loadButton.addEventListener(Event.TRIGGERED, loadButton_triggeredHandler);
			panel.addChild(loadButton)
			
			backButton = new Button();
			backButton.label = i18n.gettext("STR_CANCEL");
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			panel.addChild(backButton)
		}
		
		private function loadSavesFiles():ListCollection {
			var collection:ListCollection = new ListCollection();
			var saveFiles:Array = TTOFiles.getDirectory(Save.SAVE_DIR, TTOFiles.STORAGE_DIR);
			for each (var saveFile:File in saveFiles) {
				var save:Save = new Save();
				save.loadFile(saveFile);
				
				var saveLabel:String = save.DATAS.USERNAME + ' (Lvl:' + save.DATAS.LEVEL + ')';
				var img:Image = new Image(Assets.manager.getTexture(save.DATAS.AVATAR_ID));
				img.width = img.height = 64;
				var saveDate:Date = new Date();
				saveDate.setTime(Number(save.DATAS.LAST_SAVE));
				var accessory:Label = new Label()
				accessory.text = saveDate.toLocaleDateString();
				
				collection.push({label: saveLabel, icon: img, accessory: accessory, save: save, file: saveFile});
			}
			
			return collection;
		}
		
		private function loadButton_triggeredHandler(e:Event):void {
			Game.LOGGED_IN = true;
			Game.PROFILE_DATAS = saveList.selectedItem.save.DATAS;
			Game.PROFILE_DATAS.STATS.FORFEITS = Game.PROFILE_DATAS.STARTED_MATCHES - Game.PROFILE_DATAS.ENDED_MATCHES;
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		private function deleteButton_triggeredHandler(e:Event):void {
			alert = Alert.show(i18n.gettext("STR_DELETE_SAVE_CONFIRMATION_MESSAGE"), i18n.gettext("STR_INFORMATION"), new ListCollection([{ label:i18n.gettext('STR_CANCEL') },{ label:i18n.gettext('STR_OK') }]));
			alert.addEventListener(Event.CLOSE, alertCloseHandler);
		}
		
		private function alertCloseHandler(event:Event, data:Object):void {
			if(data) {
				if (String(data.label) == i18n.gettext('STR_OK')) {
					deleteButton.isEnabled = false;
					loadButton.isEnabled = false;
					TTOFiles.deleteFile(saveList.selectedItem.file);
					saveList.dataProvider = loadSavesFiles();
				}
			}
		}
		
		private function saveList_changeHandler(e:Event):void {
			if (saveList.selectedItem) {
				deleteButton.isEnabled = deleteButton.useHandCursor = true;
				loadButton.isEnabled = loadButton.useHandCursor = true;
			} else {
				deleteButton.isEnabled = deleteButton.useHandCursor = false;
				loadButton.isEnabled = loadButton.useHandCursor = false;
			}
		}
		
		private function backButton_triggeredHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'MENU_SCREEN');
		}
		
		override public function dispose():void {
			if (alert && alert.hasEventListener(Event.CLOSE)) alert.removeEventListener(Event.CLOSE, alertCloseHandler);
			saveList.removeEventListener(Event.CHANGE, saveList_changeHandler);
			deleteButton.removeEventListener(Event.TRIGGERED, deleteButton_triggeredHandler);
			loadButton.removeEventListener(Event.TRIGGERED, loadButton_triggeredHandler);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			super.dispose();
		}
	
	}

}