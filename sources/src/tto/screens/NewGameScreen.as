package tto.screens {
	import feathers.controls.Button;
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.Panel;
	import feathers.controls.PickerList;
	import feathers.controls.Screen;
	import feathers.controls.TextInput;
	import feathers.data.ListCollection;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.controls.AvatarChooser;
	import tto.datas.Save;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class NewGameScreen extends Screen {
		private var panel:Panel;
		private var OKBtn:Button;
		private var backBtn:Button;
		private var usernameInput:TextInput;
		private var avatarSelector:AvatarChooser;
		private var modePicker:PickerList;
		
		public function NewGameScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			
			panel.x = (stage.width - panel.width) / 2;
			panel.y = (stage.height - panel.height) / 2;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			panel = new Panel();
			panel.title = i18n.gettext("STR_NEW_GAME");
			panel.footerFactory = panelFooter;
			addChild(panel);
			
			avatarSelector = new AvatarChooser(Assets.manager.getTexture('ffxiv_twi01001'), 96);
			avatarSelector.x = avatarSelector.y = 8;
			panel.addChild(avatarSelector);
			
			var username:Label = new Label();
			username.text = i18n.gettext('STR_USERNAME');
			username.styleName = Label.ALTERNATE_STYLE_NAME_DETAIL;
			username.x = 96 + 16;
			username.y = 8;
			panel.addChild(username);
			
			usernameInput = new TextInput();
			usernameInput.prompt = 'Triad Kupo';
			usernameInput.x = 96 + 16;
			usernameInput.y = 36;
			panel.addChild(usernameInput);
			
			var mode:Label = new Label();
			mode.text = i18n.gettext('STR_MODE');
			mode.styleName = Label.ALTERNATE_STYLE_NAME_DETAIL;
			mode.x = 96 + 16;
			mode.y = 92;
			panel.addChild(mode);
			
			modePicker = new PickerList();
			modePicker.dataProvider = new ListCollection([{label: "Final Fantasy XIV", data: "ff14_"}, {label: "Final Fantasy VIII", data: "ff8_"}]);
			modePicker.x = 96 + 16;
			modePicker.y = 112;
			panel.addChild(modePicker);
		}
		
		private function panelFooter():Header {
			var footer:Header = new Header();
			OKBtn = new Button();
			OKBtn.label = i18n.gettext('STR_OK');
			OKBtn.addEventListener(Event.TRIGGERED, OKBtnHandler);
			footer.rightItems = new <DisplayObject>[OKBtn];
			
			backBtn = new Button();
			backBtn.label = i18n.gettext("STR_CANCEL");
			backBtn.addEventListener(Event.TRIGGERED, backBtnHandler);
			footer.leftItems = new <DisplayObject>[backBtn];
			return footer;
		}
		
		private function backBtnHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'MENU_SCREEN');
		}
		
		private function OKBtnHandler(e:Event):void {
			var newSave:Save = new Save();
			newSave.setToDefaultValues();
			newSave.DATAS.AVATAR_ID = avatarSelector.selectedAvatarId;
			newSave.DATAS.USERNAME = (usernameInput.text !== '') ? usernameInput.text : 'Triad Kupo';
			newSave.DATAS.MODE = String(modePicker.selectedItem.data);
			if (newSave.DATAS.MODE == "ff8_") {
				var rang_1:Array = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
				var rang_2:Array = [12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22];
				var rang_3:Array = [23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33];
				var rang_4:Array = [34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44];
				var rang_5:Array = [45, 46, 47, 49, 50, 51, 52, 53, 54, 55];
				var card_1:Array = tools.array_rand(rang_1, 2);
				var card_2:Array = tools.array_rand(rang_2, 2);
				var card_3:Array = tools.array_rand(rang_3, 2);
				var card_4:Array = tools.array_rand(rang_4, 2);
				var card_5:Array = tools.array_rand(rang_5, 2);
				var cards:Array = [card_1[0], card_1[1], card_2[0], card_2[1],card_3[0], card_3[1],card_4[0], card_4[1],card_5[0], card_5[1]];
				
				cards.sort(Array.NUMERIC);
				newSave.DATAS.CARDS = cards;
				newSave.DATAS.DECKS = [];
				newSave.DATAS.MGP = 500;
			}
			newSave.save();
			
			Game.PROFILE_DATAS = newSave.DATAS;
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		override public function dispose():void {
			OKBtn.removeEventListener(Event.TRIGGERED, OKBtnHandler);
			backBtn.removeEventListener(Event.TRIGGERED, backBtnHandler);
			super.dispose();
		}
	}
}