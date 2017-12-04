package tto.screens {
	import feathers.controls.GroupedList;
	import feathers.controls.Header;
	import feathers.controls.List;
	import feathers.controls.Panel;
	import feathers.controls.Screen;
	import feathers.data.HierarchicalCollection;
	import feathers.data.ListCollection;
	import starling.display.Button;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import starling.events.Event;
	import tto.controls.MGPLabel;
	import tto.datas.Save;
	import tto.display.UserBar;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	
	
	/**
	 * ...
	 * @author Mao
	 */
	public class GSGroupScreen extends Screen {
		private var matches:Array
		private var userBar:UserBar;
		private var panel:Panel;
		private var backButton:Button;
		private var matchesList:GroupedList;
		private var startCampaign:feathers.controls.Button;
		
		public function GSGroupScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			userBar = new UserBar('GSGROUP_SCREEN');
			addChild(userBar);
			
			panel = new Panel();
			panel.title = i18n.gettext("STR_GSGROUP");
			panel.headerFactory = headerFactory;
			panel.footerFactory = footerFactory;
			panel.x = 8;
			panel.y = 96;
			panel.horizontalScrollPolicy = Panel.SCROLL_POLICY_OFF;
			addChild(panel);
			
			matches = [
				{
					header: i18n.gettext('STR_OPPONENTS'),
					children: [
						{label:i18n.gettext('STR_NPC_TT_Master'), icon:new Image(Assets.manager.getTexture('tt-master'))},
						{label:i18n.gettext('STR_NPC_Jonas'), icon:new Image(Assets.manager.getTexture('jonas'))},
						{label:i18n.gettext('STR_NPC_Guhtwint'), icon:new Image(Assets.manager.getTexture('guhtwint'))},
						{label:i18n.gettext('STR_NPC_Aurifort'), icon:new Image(Assets.manager.getTexture('aurifort'))},
						{label:i18n.gettext('STR_NPC_Ruhtwyda'), icon:new Image(Assets.manager.getTexture('ruhtwyda'))},
						{label:i18n.gettext('STR_NPC_King_Elmer'), icon:new Image(Assets.manager.getTexture('elmer')) }
					]
				},
				{
					header: i18n.gettext('STR_RULES'),
					children: [
						{ label: i18n.gettext('STR_MATCH_FEE'), accessory:new MGPLabel(500) }
					]
				}
			];
			
			matchesList = new GroupedList();
			matchesList.dataProvider = new HierarchicalCollection(matches);
			panel.addChild(matchesList);
		}
		
		
		private function footerFactory():Header {
			var header:Header = new Header();
			startCampaign = new feathers.controls.Button();
			startCampaign.label = i18n.gettext("STR_START");
			startCampaign.isEnabled = (Game.PROFILE_DATAS.MGP >= 500) ? true : false;
			startCampaign.addEventListener(Event.TRIGGERED, startCampaignHandler);
			header.rightItems =  new <DisplayObject>[startCampaign];
			return header;
		}
		
		private function startCampaignHandler(e:Event):void {
			startCampaign.isEnabled = false
			Game.PROFILE_DATAS.STARTED_MATCHES += 1;
			Game.PROFILE_DATAS.PVE_MATCHES += 1;
			Game.PROFILE_DATAS.MGP -= 500;
			Save.save(Game.PROFILE_DATAS);
			
			dispatchEventWith('gotoScreen', false, 'GSGROUP_MATCH_SCREEN');
		}
		
		private function headerFactory():Header {
			var header:Header = new Header();
			backButton = new Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			header.leftItems =  new <DisplayObject>[backButton];
			return header;
		}
		
		private function backButton_triggeredHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		override public function dispose():void {
			startCampaign.removeEventListener(Event.TRIGGERED, startCampaignHandler);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler)
			super.dispose();
		}
	}
}