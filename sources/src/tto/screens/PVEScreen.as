package tto.screens  
{
	import com.adobe.utils.ArrayUtil;
	import feathers.controls.GroupedList;
	import feathers.controls.Header;
	import feathers.controls.Panel;
	import feathers.controls.PickerList;
	import feathers.controls.Screen;
	import feathers.data.HierarchicalCollection;
	import feathers.data.ListCollection;
	import starling.display.Button;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import starling.events.Event;
	import tto.controls.MGPLabel;
	import tto.datas.NPCs;
	import tto.datas.Save;
	import tto.datas.tripleTriadRules;
	import tto.display.UserBar;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	import tto.utils.tools;
	
	
	/**
	 * ...
	 * @author Mao
	 */
	public class PVEScreen extends Screen
	{
		private var userBar:UserBar;
		private var campaignPanel:Panel;
		private var freeModePanel:Panel;
		private var freeModeSettings:GroupedList;
		private var NPCPicker:PickerList;
		private var registerBtn:feathers.controls.Button;
		private var randomOpponentBtn:feathers.controls.Button;
		private var backButton:Button;
		private var tttBtn:Button;
		private var ccBtn:Button;
		private var gstBtn:Button;
		
		public function PVEScreen() 
		{
			super();
		}
		
		override protected function draw():void
		{
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			freeModePanel.x = campaignPanel.x + campaignPanel.width + 8;
			freeModePanel.width = stage.width - freeModePanel.x - 8;
			freeModePanel.height = stage.height - 96 - 48 - 8;
			freeModeSettings.width = freeModePanel.width - 16;
			freeModeSettings.height = freeModePanel.height - 96;
			
			NPCPicker.dispatchEvent(new Event(Event.CHANGE, false));
		}
		
		override protected function initialize():void
		{
			super.initialize();
			
			userBar = new UserBar('PVE_SCREEN');
			addChild(userBar);
			
			campaignPanel = new Panel();
			campaignPanel.title = i18n.gettext('STR_CAMPAIGNS');
			campaignPanel.headerFactory = headerFactory;
			campaignPanel.x = 8;
			campaignPanel.y = 96;
			addChild(campaignPanel);
			
			tttBtn = new Button(Assets.manager.getTexture('tt_tuto'));
			tttBtn.y = 8;
			tttBtn.addEventListener(Event.TRIGGERED, tutoHandler);
			campaignPanel.addChild(tttBtn);
			
			if (Game.PROFILE_DATAS.MODE == "ff8_") {
				ccBtn = new Button(Assets.manager.getTexture('card_club'));
				ccBtn.y = 8+64;
				ccBtn.addEventListener(Event.TRIGGERED, tccHandler);
				campaignPanel.addChild(ccBtn);
			}
			
			if (Game.PROFILE_DATAS.MODE == "ff14_") {
				gstBtn = new Button(Assets.manager.getTexture('gs_tournament'));
				gstBtn.y = 8+64;
				gstBtn.addEventListener(Event.TRIGGERED, gsHandler);
				campaignPanel.addChild(gstBtn);
			}
			
			freeModePanel = new Panel();
			freeModePanel.title = i18n.gettext('STR_FREE_MODE');
			freeModePanel.y = 96;
			freeModePanel.footerFactory = freeModeFooter;
			addChild(freeModePanel);
			
			freeModeSettings = new GroupedList();
			freeModeSettings.x = 8;
			freeModeSettings.y = 8;
			freeModeSettings.isSelectable = false;
			
			NPCPicker = new PickerList();
			NPCPicker.dataProvider = NPCs.toListCollection();
			NPCPicker.typicalItem = { label:i18n.gettext('STR_CHOOSE_NPC'), icon:new Image(Assets.manager.getTexture('tt-master')) };
			NPCPicker.addEventListener(Event.CHANGE, NPCPickerHandler);
			
			freeModeSettings.dataProvider = new HierarchicalCollection(
			[
				{
					header:i18n.gettext('STR_OPPONENT'),
					children:
					[
						{ label: i18n.gettext('STR_NPC'), accessory:NPCPicker },
					]
				},
				{	
					header:i18n.gettext('STR_RULES'),
					children:[]
				}
			]);
			freeModePanel.addChild(freeModeSettings);
		}
		
		private function NPCPickerHandler(e:Event):void {
			registerBtn.isEnabled = false;
			if (NPCPicker.selectedItem.npc.matchFee < Game.PROFILE_DATAS.MGP) {
				registerBtn.isEnabled = true;
			}
			
			var rulesList:Array = [];
			rulesList.push({ label: i18n.gettext('STR_MATCH_FEE'), accessory:new MGPLabel(NPCPicker.selectedItem.npc.matchFee)});
			for each(var rule:String in NPCPicker.selectedItem.npc.rules) {
				rulesList.push({ label: i18n.gettext(rule)});
			}
			
			var provider:HierarchicalCollection = new HierarchicalCollection(freeModeSettings.dataProvider.data);
			provider.data[1].children = rulesList;
			
			freeModeSettings.dataProvider = provider;
		}
		
		private function freeModeFooter():Header {
			var footer:Header = new Header();
			registerBtn = new feathers.controls.Button();
			registerBtn.isEnabled = false;
			registerBtn.label = i18n.gettext('STR_REGISTER_MATCH');
			registerBtn.addEventListener(Event.TRIGGERED, registerBtnHandler);
			
			randomOpponentBtn = new feathers.controls.Button();
			randomOpponentBtn.label = i18n.gettext('STR_RANDOM_OPPONENT');
			randomOpponentBtn.addEventListener(Event.TRIGGERED, randomBtnHandler);
			
			footer.rightItems = new <DisplayObject>
			[
				randomOpponentBtn,
				registerBtn
			];
			return footer;
		}
		
		private function randomBtnHandler(e:Event):void {
			var randomizer:Array, randomCards:Array, i:uint, rand:uint;
			NPCPicker.selectedIndex = tools.rand(NPCPicker.dataProvider.length - 1);
			
			var npcRules:Array = NPCPicker.selectedItem.npc.rules;
			var gameRules:Object = {
				OPEN_RULE:tripleTriadRules.RULE_DEFAULT_OPEN,
				SUDDEN_DEATH:false,
				RANDOM:false,
				ORDER:tripleTriadRules.RULE_DEFAULT_ORDER,
				REVERSE:false,
				FALLEN_ACE:false,
				SAME:false,
				SAME_WALL:false,
				PLUS:false,
				TYPE_RULE:tripleTriadRules.RULE_DEFAULT_TYPE,
				SWAP:false,
				ROULETTE:false
			};
			
			for each (var rule:String in npcRules) {
				switch (rule) {
					case (tripleTriadRules.RULE_ALL_OPEN):
						gameRules.OPEN_RULE = rule;
						break;
					case (tripleTriadRules.RULE_THREE_OPEN):
						gameRules.OPEN_RULE = rule;
						break;
					case (tripleTriadRules.RULE_SUDDEN_DEATH):
						gameRules.SUDDEN_DEATH = true;
						break;
					case (tripleTriadRules.RULE_RANDOM):
						gameRules.RANDOM = true;
						break;
					case (tripleTriadRules.RULE_ORDER):
						gameRules.ORDER = rule;
						break;
					case (tripleTriadRules.RULE_CHAOS):
						gameRules.ORDER = rule;
						break;
					case (tripleTriadRules.RULE_REVERSE):
						gameRules.REVERSE = true;
						break;
					case (tripleTriadRules.RULE_FALLEN_ACE):
						gameRules.FALLEN_ACE = true;
						break;
					case (tripleTriadRules.RULE_SAME):
						gameRules.SAME = true;
						break;
					case (tripleTriadRules.RULE_SAME_WALL):
						gameRules.SAME_WALL = true;
						break;
					case (tripleTriadRules.RULE_PLUS):
						gameRules.PLUS = true;
						break;
					case (tripleTriadRules.RULE_ASCENSION):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_DESCENSION):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_ELEMENTAL):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_SWAP):
						gameRules.SWAP = true;
						break;
					case (tripleTriadRules.RULE_ROULETTE):
						gameRules.ROULETTE = true;
						break;
				}
			}
			
			var userCards:Array = [];
			var npcCards:Array = NPCPicker.selectedItem.npc.getRandomCards();
			
			Game.PROFILE_DATAS.STARTED_MATCHES += 1;
			Game.PROFILE_DATAS.PVE_MATCHES += 1;
			//Game.PROFILE_DATAS.MGP -= NPCPicker.selectedItem.npc.matchFee;
			Save.save(Game.PROFILE_DATAS);
			this.dispatchEventWith('pve_free_mode', false, { RULES:gameRules, BLUE_CARDS:userCards, RED_CARDS:npcCards, RED_PLAYER_NAME:NPCPicker.selectedItem.label, _NPC:NPCPicker.selectedItem.npc });
		}
		
		private function registerBtnHandler(e:Event):void 
		{
			var randomizer:Array, randomCards:Array, i:uint, rand:uint;
			
			var npcRules:Array = NPCPicker.selectedItem.npc.rules;
			var gameRules:Object = {
				OPEN_RULE:tripleTriadRules.RULE_DEFAULT_OPEN,
				SUDDEN_DEATH:false,
				RANDOM:false,
				ORDER:tripleTriadRules.RULE_DEFAULT_ORDER,
				REVERSE:false,
				FALLEN_ACE:false,
				SAME:false,
				SAME_WALL:false,
				PLUS:false,
				TYPE_RULE:tripleTriadRules.RULE_DEFAULT_TYPE,
				SWAP:false,
				ROULETTE:false
			};
			
			for each (var rule:String in npcRules) {
				switch (rule) {
					case (tripleTriadRules.RULE_ALL_OPEN):
						gameRules.OPEN_RULE = rule;
						break;
					case (tripleTriadRules.RULE_THREE_OPEN):
						gameRules.OPEN_RULE = rule;
						break;
					case (tripleTriadRules.RULE_SUDDEN_DEATH):
						gameRules.SUDDEN_DEATH = true;
						break;
					case (tripleTriadRules.RULE_RANDOM):
						gameRules.RANDOM = true;
						break;
					case (tripleTriadRules.RULE_ORDER):
						gameRules.ORDER = rule;
						break;
					case (tripleTriadRules.RULE_CHAOS):
						gameRules.ORDER = rule;
						break;
					case (tripleTriadRules.RULE_REVERSE):
						gameRules.REVERSE = true;
						break;
					case (tripleTriadRules.RULE_FALLEN_ACE):
						gameRules.FALLEN_ACE = true;
						break;
					case (tripleTriadRules.RULE_SAME):
						gameRules.SAME = true;
						break;
					case (tripleTriadRules.RULE_SAME_WALL):
						gameRules.SAME_WALL = true;
						break;
					case (tripleTriadRules.RULE_PLUS):
						gameRules.PLUS = true;
						break;
					case (tripleTriadRules.RULE_ASCENSION):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_DESCENSION):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_ELEMENTAL):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_SWAP):
						gameRules.SWAP = true;
						break;
					case (tripleTriadRules.RULE_ROULETTE):
						gameRules.ROULETTE = true;
						break;
				}
			}
			
			var userCards:Array = [];
			var npcCards:Array = NPCPicker.selectedItem.npc.getRandomCards();
			
			Game.PROFILE_DATAS.STARTED_MATCHES += 1;
			Game.PROFILE_DATAS.PVE_MATCHES += 1;
			Game.PROFILE_DATAS.MGP -= NPCPicker.selectedItem.npc.matchFee;
			Save.save(Game.PROFILE_DATAS);
				
			this.dispatchEventWith('pve_free_mode', false, { RULES:gameRules, BLUE_CARDS:userCards, RED_CARDS:npcCards, RED_PLAYER_NAME:NPCPicker.selectedItem.label, _NPC:NPCPicker.selectedItem.npc });
		}
		
		private function tutoHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'TUTORIAL_SCREEN');
		}
		
		private function tccHandler(e:Event):void 
		{
			dispatchEventWith('gotoScreen', false, 'CCGROUP_SCREEN');
		}
		
		private function gsHandler(e:Event):void 
		{
			dispatchEventWith('gotoScreen', false, 'GSGROUP_SCREEN');
		}
		
		private function headerFactory():Header {
			var header:Header = new Header();
			backButton = new Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			header.leftItems =  new <DisplayObject>[backButton];
			return header;
		}
		
		private function backButton_triggeredHandler(e:Event):void 
		{
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		override public function dispose():void
		{
			tttBtn.removeEventListener(Event.TRIGGERED, tutoHandler);
			if (ccBtn) ccBtn.removeEventListener(Event.TRIGGERED, tccHandler);
			if (gstBtn) gstBtn.removeEventListener(Event.TRIGGERED, gsHandler);
			NPCPicker.removeEventListener(Event.CHANGE, NPCPickerHandler);
			registerBtn.removeEventListener(Event.TRIGGERED, registerBtnHandler);
			randomOpponentBtn.removeEventListener(Event.TRIGGERED, randomBtnHandler);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			super.dispose();
		}
	}
}