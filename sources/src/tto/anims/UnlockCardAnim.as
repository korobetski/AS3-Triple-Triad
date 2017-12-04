package tto.anims 
{
	import starling.animation.Transitions;
	import starling.core.Starling;
	import starling.display.DisplayObjectContainer;
	import tto.display.Card;
	import tto.Game;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class UnlockCardAnim extends DisplayObjectContainer 
	{
		private var card:Card;
		
		
		public function UnlockCardAnim() 
		{
			super();
		}
		
		public function start(cardId:uint):void {
			this.touchable = false;
			card = new Card();
			card.x = stage.width * 0.75;
			card.y = 0;
			card.alpha = 0;
			card.scaleX = card.scaleY = 1.2;
			card.color = "BLUE"
			card.rotation = 55 * Math.PI / 180;
			card.draw(String(cardId), Game.PROFILE_DATAS.MODE);
			addChild(card);			
			
			Starling.juggler.tween(card, 0.3, { transition: Transitions.EASE_OUT, x:308+116, y:166+116, rotation:0, scaleX:1.5, scaleY:1.5, alpha: 1, onComplete:predispose } );
		}
		
		
		private function predispose():void {
			Starling.juggler.tween(card, 0.2, { delay: 1.4, x:0, y:stage.height * 0.75, scaleX:1, scaleY:1, alpha: 0, onComplete:dispose } );
		}
		
		override public function dispose():void 
		{
			this.removeChild(card);
			super.dispose();
		}
		
	}

}