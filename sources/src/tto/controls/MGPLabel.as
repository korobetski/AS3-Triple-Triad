package tto.controls {
	import feathers.controls.Label;
	import feathers.controls.LayoutGroup;
	import feathers.layout.HorizontalLayout;
	import starling.display.Image;
	import starling.filters.BlurFilter;
	import tto.utils.Assets;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class MGPLabel extends LayoutGroup {
		private var label:Label;
		private var PGSIcon:Image;
		private var _value:uint;
		
		public function MGPLabel(MGP_value:uint) {
			super();
			
			_value = MGP_value;
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
			
			PGSIcon = new Image(Assets.manager.getTexture('PGS'));
			PGSIcon.width = PGSIcon.height = 29;
			this.addChild(PGSIcon);
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
		
		override public function dispose():void {
			tools.purge(this)
			super.dispose();
		}
	
	}

}