package tto.screens 
{
	import feathers.controls.Label;
	import feathers.controls.LayoutGroup;
	import feathers.layout.VerticalLayout;
	import starling.display.Quad;
	import starling.filters.BlurFilter;
	import tto.datas.tripleTriadRules;
	import tto.utils.i18n;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class RulesDigest extends LayoutGroup 
	{
		private var _rules:Object;
		
		public function RulesDigest(matchRules:Object) 
		{
			super();
			
			_rules = matchRules;
			this.width = 250;
			display();
			
			var VL:VerticalLayout = new VerticalLayout();
			//VL.horizontalAlign = VerticalLayout.HORIZONTAL_ALIGN_CENTER;
			VL.padding = 8;
			VL.gap = 2;
			this.layout = VL;
		}
		
		private function display():void {
			tools.purge(this);
			
			if (_rules.OPEN_RULE !== tripleTriadRules.RULE_DEFAULT_OPEN) addLabel(_rules.OPEN_RULE);
			if (_rules.SUDDEN_DEATH) addLabel(tripleTriadRules.RULE_SUDDEN_DEATH);
			if (_rules.RANDOM) addLabel(tripleTriadRules.RULE_RANDOM);
			if (_rules.ORDER !== tripleTriadRules.RULE_DEFAULT_ORDER) addLabel(_rules.ORDER);
			if (_rules.REVERSE) addLabel(tripleTriadRules.RULE_REVERSE);
			if (_rules.FALLEN_ACE) addLabel(tripleTriadRules.RULE_FALLEN_ACE);
			if (_rules.SAME) addLabel(tripleTriadRules.RULE_SAME);
			if (_rules.SAME_WALL) addLabel(tripleTriadRules.RULE_SAME_WALL);
			if (_rules.PLUS) addLabel(tripleTriadRules.RULE_PLUS);
			if (_rules.TYPE_RULE !== tripleTriadRules.RULE_DEFAULT_TYPE) addLabel(_rules.TYPE_RULE);
			if (_rules.SWAP) addLabel(tripleTriadRules.RULE_SWAP);
		}
		
		public function get activeRules():Array {
			var rules:Array = [];			
			if (_rules.OPEN_RULE !== tripleTriadRules.RULE_DEFAULT_OPEN) rules.push(_rules.OPEN_RULE);
			if (_rules.SUDDEN_DEATH) rules.push(tripleTriadRules.RULE_SUDDEN_DEATH);
			if (_rules.RANDOM) rules.push(tripleTriadRules.RULE_RANDOM);
			if (_rules.ORDER !== tripleTriadRules.RULE_DEFAULT_ORDER) rules.push(_rules.ORDER);
			if (_rules.REVERSE) rules.push(tripleTriadRules.RULE_REVERSE);
			if (_rules.FALLEN_ACE) rules.push(tripleTriadRules.RULE_FALLEN_ACE);
			if (_rules.SAME) rules.push(tripleTriadRules.RULE_SAME);
			if (_rules.SAME_WALL) rules.push(tripleTriadRules.RULE_SAME_WALL);
			if (_rules.PLUS) rules.push(tripleTriadRules.RULE_PLUS);
			if (_rules.TYPE_RULE !== tripleTriadRules.RULE_DEFAULT_TYPE) rules.push(_rules.TYPE_RULE);
			if (_rules.SWAP) rules.push(tripleTriadRules.RULE_SWAP);
			if (_rules.ROULETTE) rules.push(tripleTriadRules.RULE_ROULETTE);
			
			return rules;
		}
		
		private function addLabel(text:String):void {
			var _label:Label = new Label();
			_label.text = i18n.gettext(text);
			_label.filter = BlurFilter.createDropShadow(0, 0, 0x000000, 0.8, 0.2, 0.5);
			_label.width = 250;
			this.addChild(_label);
		}
		
		override protected function draw():void 
		{
			super.draw();
			this.backgroundSkin = new Quad(250, 100, 0x000000);
			this.backgroundSkin.alpha = 0.75;
		}
		override public function dispose():void 
		{
			tools.purge(this);
			super.dispose();
		}
		public function get rules():Object 
		{
			return _rules;
		}
		
		public function set rules(value:Object):void 
		{
			_rules = value;
			display()
		}
	}

}