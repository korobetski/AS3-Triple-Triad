package tto.screens  
{
	import feathers.controls.Header;
	import feathers.controls.Panel;
	import feathers.controls.Screen;
	import feathers.controls.TextArea;
	import starling.display.Button;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.datas.Save;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	
	
	/**
	 * ...
	 * @author Mao
	 */
	public class BackstageScreen extends Screen
	{
		private var profileJSON:TextArea;
		private var backButton:Button;
		private var saveBtn:feathers.controls.Button;
		
		public function BackstageScreen() 
		{
			super();
		}
		
		override protected function draw():void
		{
			super.draw();
		}
		
		override protected function initialize():void
		{
			super.initialize();
			
			
			
			if (Game.PROFILE_DATAS.ADMIN) {
				var adminPanel:Panel = new Panel();
				adminPanel.title = i18n.gettext("Profile Editor");
				adminPanel.x = 8;
				adminPanel.y = 150;
				adminPanel.headerFactory = headerFactory;
				adminPanel.footerFactory = adminPanelFooter;
				addChild(adminPanel);
				
				profileJSON = new TextArea();
				profileJSON.width = 1024 / 2 -32;
				profileJSON.height = 768 - 300;
				profileJSON.text = JSON.stringify(Game.PROFILE_DATAS);
				adminPanel.addChild(profileJSON);
			}
		}
		
		
		
		private function backButton_triggeredHandler(e:Event):void 
		{
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		
		private function headerFactory():Header
		{
			var header:Header = new Header();
			
			backButton = new Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			
			header.leftItems =  new <DisplayObject>
			[
				backButton
			];
			return header;
		}
		
		private function adminPanelFooter():Header 
		{
			var footer:Header = new Header();
			
			saveBtn = new feathers.controls.Button();
			saveBtn.label = "Save";
			saveBtn.addEventListener(Event.TRIGGERED, saveBtnHandler);
			
			footer.leftItems =  new <DisplayObject>
			[
				saveBtn
			];
			return footer;
		}
		
		private function saveBtnHandler(e:Event):void 
		{
			Game.PROFILE_DATAS = JSON.parse(profileJSON.text);
			Save.save(Game.PROFILE_DATAS);
		}
		
		override public function dispose():void
		{
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			saveBtn.removeEventListener(Event.TRIGGERED, saveBtnHandler);
			super.dispose();
		}
	}
}