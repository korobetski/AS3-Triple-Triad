package tto.anims 
{
	import flash.utils.setTimeout;
	import starling.animation.Transitions;
	import starling.core.Starling;
	import starling.display.Image;
	import starling.display.Sprite;
	import starling.events.Event;
	import tto.utils.Assets;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class ReverseAnim extends Sprite 
	{
		private var gfx:Image;
		
		public function ReverseAnim() 
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
			
			gfx = new Image(Assets.manager.getTexture('121613.tex'));
			gfx.pivotX = gfx.width / 2;
			gfx.pivotY = gfx.height / 2;
			this.addChild(gfx);
			gfx.x = stage.width / 2;
			gfx.y = stage.height / 2;
			gfx.scaleX = 2;
			gfx.scaleY = 1.2;
			gfx.alpha = 0;
			
			Starling.juggler.tween(gfx, 0.4, { transition: Transitions.EASE_IN, scaleX:1, scaleY:1, alpha:1, onComplete:predispose } );
		}
		
		private function predispose():void {
			Starling.juggler.tween(gfx, 0.4, { transition: Transitions.EASE_IN, delay:0.6, alpha:0, onComplete:dispose } );
		}
		
		override public function dispose():void 
		{
			this.removeChild(gfx);
			super.dispose();
		}
	}

}