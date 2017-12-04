package tto.anims 
{
	import flash.utils.setTimeout;
	import starling.core.Starling;
	import starling.display.Image;
	import starling.display.Sprite;
	import starling.events.Event;
	import tto.utils.Assets;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class PlusAnim extends Sprite 
	{
		private var gfx:Image;
		
		public function PlusAnim() 
		{
			super();
			
			addEventListener(Event.ADDED_TO_STAGE, init);
		}
		
		private function init(e:Event):void 
		{
			removeEventListener(Event.ADDED_TO_STAGE, init);
			
			this.touchable = false;
			this.width = stage.width;
			this.height = stage.height;
			
			gfx = new Image(Assets.manager.getTexture('121619.tex'));
			gfx.pivotX = gfx.width / 2;
			gfx.pivotY = gfx.height / 2;
			this.addChild(gfx);
			gfx.x = stage.width / 2;
			gfx.y = stage.height / 2;
			gfx.scaleX = 2
			gfx.scaleY = 2;
			gfx.alpha = 0;
			
			Starling.juggler.tween(gfx, 0.4, {scaleX:1, scaleY:1, alpha:1, onComplete:predispose } );
		}
		
		private function predispose():void {
			Starling.juggler.tween(gfx, 0.2, {delay:0.6, scaleX:2, scaleY:2, alpha:0, onComplete:dispose } );
		}
		
		override public function dispose():void 
		{
			this.removeChild(gfx);
			super.dispose();
		}
	}

}