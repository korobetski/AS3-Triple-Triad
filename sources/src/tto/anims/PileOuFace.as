package tto.anims 
{
	import flash.utils.setTimeout;
	import starling.animation.Transitions;
	import starling.core.Starling;
	import starling.display.DisplayObjectContainer;
	import tto.display.Card;
	import tto.utils.SoundManager;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class PileOuFace extends DisplayObjectContainer 
	{
		private var _rolls:Array;
		private var card1:Card;
		private var card2:Card;
		private var card3:Card;
		
		private var blueCount:uint;
		private var redCount:uint;
		
		
		public function PileOuFace() 
		{
			super();
			this.touchable = false;
		}
		
		public function start():void {
			
			if (!_rolls) this.rolls = [tools.rand(1), tools.rand(1), tools.rand(1)]
			
			card1 = new Card();
			card1.x = stage.width * 0.75;
			card1.y = 0;
			card1.alpha = 0;
			card1.scaleX = card1.scaleY = 1.2;
			if (_rolls[0]) card1.draw('blue');
			else card1.draw('red');
			addChild(card1);
			card1.show();
			
			card2 = new Card();
			card2.x = 0;
			card2.y = stage.height;
			card2.alpha = 0;
			card2.scaleX = card1.scaleY = 1.2;
			if (_rolls[1]) card2.draw('blue');
			else card2.draw('red');
			addChild(card2);
			card2.show();
			
			card3 = new Card();
			card3.x = stage.width;
			card3.y = stage.height*0.25;
			card3.alpha = 0;
			card3.scaleX = card1.scaleY = 1.2;
			if (_rolls[2]) card3.draw('blue');
			else card3.draw('red');
			addChild(card3);
			card3.show();
			/*
			SoundManager.playSound('card', true);
			setTimeout(SoundManager.playSound, 60, 'card', true);
			setTimeout(SoundManager.playSound, 120, 'card', true);
			*/
			
			SoundManager.playSound('se_ttriad.scd_156', true);
			
			Starling.juggler.tween(card1, 0.3, { transition: Transitions.EASE_OUT, x:308+116, y:166+116, rotation: 55 * Math.PI / 180, scaleX:1, scaleY:1, alpha: 1 } );
			Starling.juggler.tween(card2, 0.3, { delay:0.1, transition: Transitions.EASE_OUT, x:308+136+88, y:166+136+48, rotation: 45 * Math.PI / 180, scaleX:1, scaleY:1, alpha: 1 } );
			Starling.juggler.tween(card3, 0.3, { delay:0.2, transition: Transitions.EASE_OUT, x:308+136+150, y:166+136+150, rotation: 35 * Math.PI / 180, scaleX:1, scaleY:1, alpha: 1, onComplete:predispose } );
		}
		
		
		private function predispose():void {
			Starling.juggler.tween(card1, 0.2, { x:0, y:stage.height * 0.75, alpha: 0 } );
			Starling.juggler.tween(card2, 0.2, { x:stage.width, y:0, alpha: 0 } );
			Starling.juggler.tween(card3, 0.2, { x:stage.width*0.25, y:stage.height, alpha: 0, onComplete:dispose } );
		}
		
		override public function dispose():void 
		{
			this.removeChild(card1);
			this.removeChild(card2);
			this.removeChild(card3);
			super.dispose();
		}
		
		public function get blueOrRed():String {
			return (blueCount > redCount) ? 'blue' : 'red';
		}
		
		public function get rolls():Array 
		{
			return _rolls;
		}
		public function randomRolls():void 
		{
			blueCount = 0;
			redCount = 0;
			
			_rolls = [tools.rand(1), tools.rand(1), tools.rand(1)]
			
			if (_rolls[0]) blueCount++;
			else redCount++
			if (_rolls[1]) blueCount++;
			else redCount++
			if (_rolls[2]) blueCount++;
			else redCount++;
		}
		public function set rolls(value:Array):void 
		{
			blueCount = 0;
			redCount = 0;
			
			_rolls = value;
			
			//if (!value) _rolls = [tools.rand(1), tools.rand(1), tools.rand(1)]
			
			if (_rolls[0]) blueCount++;
			else redCount++
			if (_rolls[1]) blueCount++;
			else redCount++
			if (_rolls[2]) blueCount++;
			else redCount++;
		}
		
	}

}