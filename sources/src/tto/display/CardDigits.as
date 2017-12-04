package tto.display 
{
	import starling.display.Image;
	import starling.display.Sprite;
	import tto.utils.Assets;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class CardDigits extends Sprite 
	{
		private static const positions:Array = [{x:14, y:0},{x:26, y:6},{x:14, y:12},{x:2, y:6}];
		
		public function CardDigits() 
		{
			super();
		}
		
		public function display(power:Array):void {
			tools.purge(this);
			// power [top, right, bottom, left];
			var cdbg:Image = new Image(Assets.manager.getTexture('cdbg'));
			cdbg.alpha = 0.5;
			cdbg.x = 8;
			cdbg.y = 1;
			this.addChild(cdbg);
			for (var i:uint = 0; i < 4; i++ ) {
				var digit:Image = new Image(Assets.manager.getTexture('cd' + String(power[i])));
				digit.x = CardDigits.positions[i].x;
				digit.y = CardDigits.positions[i].y;
				this.addChild(digit);
			}
		}
	}

}