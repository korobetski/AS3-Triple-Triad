package tto.controls 
{
	import starling.display.DisplayObjectContainer;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class cardScore extends DisplayObjectContainer 
	{
		
		public function cardScore() 
		{
			super();
			for (var i:uint = 0; i < 10; i++) {
				var newCardAvatar:cardAvatar = (i < 5) ? new cardAvatar(cardAvatar.BLUE_COLOR) : new cardAvatar(cardAvatar.RED_COLOR);
				newCardAvatar.x = (i < 5) ? i * 25 : (i-5) * 25 + 2 * 25+20;
				if (i >= 5) newCardAvatar.y = 29;
				addChild(newCardAvatar);
			}
		}
		
		public function set scores(_scores:Object):void {
			for (var i:uint = 0; i < 10; i++) {
				var ava:cardAvatar =  this.getChildAt(i) as cardAvatar;
				ava.color = (_scores.BLUE > i) ? cardAvatar.BLUE_COLOR : cardAvatar.RED_COLOR;
			}
		}
		
		override public function dispose():void 
		{
			tools.purge(this);
			super.dispose();
		}
		
	}

}