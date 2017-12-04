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
	public class AllOpenAnim extends Sprite 
	{
		private var gfx:Image;
		
		public function AllOpenAnim() 
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
			
			gfx = new Image(Assets.manager.getTexture('121611.tex'));
			gfx.pivotX = gfx.width / 2;
			gfx.pivotY = gfx.height / 2;
			this.addChild(gfx);
			gfx.x = stage.width;
			gfx.y = stage.height / 2;
			gfx.alpha = 0;
			
			Starling.juggler.tween(gfx, 0.3, { transition: Transitions.EASE_IN, delay:0.3, x:stage.width / 2, alpha:1, onComplete:predispose } );
		}
		
		private function predispose():void {
			Starling.juggler.tween(gfx, 0.2, { transition: Transitions.EASE_IN, delay:0.8, x:0, alpha:0, onComplete:dispose } );
		}
		
		override public function dispose():void 
		{
			this.removeChild(gfx);
			super.dispose();
		}
	}

}