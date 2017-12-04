package tto.screens  
{
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.List;
	import feathers.controls.Panel;
	import feathers.controls.Screen;
	import feathers.controls.text.TextFieldTextRenderer;
	import feathers.data.ListCollection;
	import flash.text.TextFormat;
	import starling.display.Button;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.datas.tripleTriadRules;
	import tto.display.UserBar;
	import tto.utils.Assets;
	import tto.utils.i18n;
	
	
	/**
	 * ...
	 * @author Mao
	 */
	public class HelpScreen extends Screen
	{
		private var userBar:UserBar;
		private var panel:Panel;
		private var backButton:Button;
		private var ruleList:List;
		private var ruleDescription:TextFieldTextRenderer;
		private var ruleName:Label;
		private var descFormat:TextFormat;
		
		public function HelpScreen() 
		{
			super();
		}
		
		override protected function draw():void
		{
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			panel.width = stage.width - 16;
			panel.height = stage.height - 96 - 48;
			
			ruleName.x = ruleList.x + ruleList.width + 16;
			ruleDescription.x = ruleList.x + ruleList.width + 16;
			ruleDescription.y = ruleName.y + ruleName.height + 32;
			ruleDescription.width = panel.width - (ruleList.x + ruleList.width + 32)
		}
		
		override protected function initialize():void
		{
			super.initialize();
			
			userBar = new UserBar('HELP_SCREEN');
			addChild(userBar);
			
			panel = new Panel();
			panel.title = i18n.gettext('STR_HELP');
			panel.headerFactory = headerFactory;
			panel.x = 8;
			panel.y = 96;
			addChild(panel);
			
			ruleList = new List();
			ruleList.itemRendererProperties.height = 30;
			ruleList.dataProvider = new ListCollection([
				{label:i18n.gettext(tripleTriadRules.RULE_ALL_OPEN), code:tripleTriadRules.RULE_ALL_OPEN},
				{label:i18n.gettext(tripleTriadRules.RULE_THREE_OPEN), code:tripleTriadRules.RULE_THREE_OPEN},
				{label:i18n.gettext(tripleTriadRules.RULE_SUDDEN_DEATH), code:tripleTriadRules.RULE_SUDDEN_DEATH},
				{label:i18n.gettext(tripleTriadRules.RULE_RANDOM), code:tripleTriadRules.RULE_RANDOM},
				{label:i18n.gettext(tripleTriadRules.RULE_ORDER), code:tripleTriadRules.RULE_ORDER},
				{label:i18n.gettext(tripleTriadRules.RULE_CHAOS), code:tripleTriadRules.RULE_CHAOS},
				{label:i18n.gettext(tripleTriadRules.RULE_REVERSE), code:tripleTriadRules.RULE_REVERSE },
				{label:i18n.gettext(tripleTriadRules.RULE_FALLEN_ACE), code:tripleTriadRules.RULE_FALLEN_ACE},
				{label:i18n.gettext(tripleTriadRules.RULE_SAME), code:tripleTriadRules.RULE_SAME},
				{label:i18n.gettext(tripleTriadRules.RULE_SAME_WALL), code:tripleTriadRules.RULE_SAME_WALL},
				{label:i18n.gettext(tripleTriadRules.RULE_PLUS), code:tripleTriadRules.RULE_PLUS},
				{label:i18n.gettext(tripleTriadRules.RULE_COMBO), code:tripleTriadRules.RULE_COMBO},
				{label:i18n.gettext(tripleTriadRules.RULE_CHAOS), code:tripleTriadRules.RULE_CHAOS},
				{label:i18n.gettext(tripleTriadRules.RULE_ASCENSION), code:tripleTriadRules.RULE_ASCENSION },
				{label:i18n.gettext(tripleTriadRules.RULE_DESCENSION), code:tripleTriadRules.RULE_DESCENSION},
				{label:i18n.gettext(tripleTriadRules.RULE_ELEMENTAL), code:tripleTriadRules.RULE_ELEMENTAL},
				{label:i18n.gettext(tripleTriadRules.RULE_SWAP), code:tripleTriadRules.RULE_SWAP},
				{label:i18n.gettext(tripleTriadRules.RULE_ROULETTE), code:tripleTriadRules.RULE_ROULETTE},
			])
			ruleList.addEventListener(Event.CHANGE, ruleListHandler);
			panel.addChild(ruleList);
			
			ruleName = new Label();
			ruleName.styleName = Label.ALTERNATE_STYLE_NAME_HEADING;
			panel.addChild(ruleName);
			
			descFormat = new TextFormat('Raleway', 14, 0xeeeeee);
			ruleDescription = new TextFieldTextRenderer();
			ruleDescription.wordWrap = true;
			ruleDescription.textFormat = descFormat;
			ruleDescription.embedFonts = true;
			ruleDescription.isHTML = true;
			panel.addChild(ruleDescription);
		}
		
		private function ruleListHandler(e:Event):void {
			var datas:Object = ruleList.selectedItem
			ruleName.text = datas.label;
			ruleDescription.text = i18n.gettext(datas.code+"_HELP");
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
			ruleList.removeEventListener(Event.CHANGE, ruleListHandler);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			super.dispose();
		}
	}
}