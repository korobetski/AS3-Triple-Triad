package tto.controls {
	import feathers.controls.Label;
	import feathers.controls.LayoutGroup;
	import feathers.layout.HorizontalLayout;
	import starling.display.Image;
	import starling.filters.BlurFilter;
	import tto.utils.Assets;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class XPLabel extends LayoutGroup {
		private var label:Label;
		private var XPIcon:Image;
		private var _value:uint;
		
		public function XPLabel(XP_value:uint) {
			super();
			
			_value = XP_value;
		}
		
		override protected function initialize():void {
			super.initialize();
			this.touchable = false;
			var HL:HorizontalLayout = new HorizontalLayout();
			HL.padding = 8;
			HL.gap = 4;
			HL.verticalAlign = HorizontalLayout.VERTICAL_ALIGN_MIDDLE
			this.layout = HL;
			
			label = new Label();
			label.text = String(_value);
			label.filter = BlurFilter.createDropShadow(0, 0, 0x000000, 1.2, 0.2, 0.5);
			this.addChild(label);
			
			XPIcon = new Image(Assets.manager.getTexture('XP'));
			XPIcon.width = XPIcon.height = 29;
			this.addChild(XPIcon);
		}
		
		override protected function draw():void {
			super.draw();
		}
		
		public function set text(value:String):void {
			this.label.text = value;
		}
		
		public function get text():String {
			return this.label.text;
		}
	
	}

}